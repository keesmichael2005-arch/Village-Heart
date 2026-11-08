package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.GRID;
import static dev.villageheart.gametest.BuildTestSupport.YARD;
import static dev.villageheart.gametest.BuildTestSupport.env;

import dev.villageheart.block.MarkerStatus;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.LevelSiteView;
import dev.villageheart.build.PaletteStates;
import dev.villageheart.build.SiteValidation;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Rank;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.blueprint.SiteRules;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.gametest.BuildTestSupport.With;
import dev.villageheart.room.RoomCells;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Layer 2: the builder and the blueprints (M20). Rules (docs/TESTING.md, "Builder GameTests"): own environment per test, the {@code yard}
 * structure with {@code skyAccess}, {@link BuildTestSupport#layout} first and {@code VillageTestSupport.done} last.
 */
public class BuildGameTests {
	// ---- T-L2-1 ----------------------------------------------------------------------------------

	/** The core annotations (cell kind, contents) of every palette entry agree with the real block states, in all four rotations. */
	@GameTest(maxTicks = 100)
	public void paletteAgreesWithRoomCells(GameTestHelper h) {
		for (Blueprint bp : Blueprints.all()) {
			for (Rotation4 r : Rotation4.values()) {
				for (PaletteEntry e : bp.palette().values()) {
					if (!e.isBlock()) continue;
					BlockState template = PaletteStates.template(e.state());
					String what = bp.id() + " " + r + " " + e.symbol() + " " + e.state();
					// the unsubstituted state: kind, contents and partner are exactly the annotation
					BlockState own = PaletteStates.resolve(e, r, null);
					Cell real = RoomCells.of(own);
					Cell expected = e.scannerCell();
					h.assertTrue(real.kind() == expected.kind(), what + ": cell kind " + real.kind() + " vs " + expected.kind());
					h.assertTrue(real.door() == expected.door(), what + ": door part " + real.door() + " vs " + expected.door());
					Dir expectedPartner = expected.partner() == null ? null : r.apply(expected.partner());
					h.assertTrue(real.partner() == expectedPartner, what + ": partner " + real.partner() + " vs " + expectedPartner);
					if (!e.blockId().equals("villageheart:house_marker") && !e.blockId().equals("villageheart:storage_marker")) {
						h.assertTrue(new TreeSet<>(real.contents()).equals(new TreeSet<>(expected.contents())),
								what + ": contents " + real.contents() + " vs " + expected.contents());
					}
					if (e.material() == null) continue;
					// a tag material places the block of the item used: every property of the template survives, the cell is the same
					Item item = PaletteStates.displayItem(e.material());
					h.assertTrue(item != null, what + ": the material has an item");
					h.assertTrue(PaletteStates.satisfies(e.material(), item), what + ": the display item satisfies the material");
					Block sub = PaletteStates.substituteFor(e, item);
					if (e.material().isTag()) {
						h.assertTrue(sub != null, what + ": a block for the tag material's item");
						BlockState subst = PaletteStates.resolve(e, r, sub);
						for (Property<?> p : template.getProperties()) {
							h.assertTrue(subst.hasProperty(p), what + ": the substituted block has the property " + p.getName());
						}
						Cell s = RoomCells.of(subst);
						h.assertTrue(s.kind() == expected.kind() && s.door() == expected.door(), what + ": substituted cell kind");
					} else if (!e.converts()) {
						String itemId = BuiltInRegistries.ITEM.getKey(own.getBlock().asItem()).toString();
						h.assertTrue(itemId.equals(e.material().id()), what + ": exact material " + e.material().id() + " is the item " + itemId);
					}
				}
			}
		}
		// a part gets the same block as its primary: a red bed foot has a red bed head
		PaletteEntry foot = Blueprints.SMALL_HOUSE.palette().get('B'), head = Blueprints.SMALL_HOUSE.palette().get('b');
		Block red = PaletteStates.substituteFor(foot, Items.RED_BED);
		h.assertTrue(red == Blocks.RED_BED, "red bed from the red bed item");
		h.assertTrue(PaletteStates.resolve(head, Rotation4.NONE, red).getBlock() == Blocks.RED_BED, "the red bed's head is red too");
		h.assertTrue(PaletteStates.resolve(head, Rotation4.NONE, red).getValue(BedBlock.PART) == BedPart.HEAD, "and a head");
		// the conversions are exactly the whitelist
		int converts = 0;
		for (Blueprint bp : Blueprints.all()) for (PaletteEntry e : bp.palette().values()) if (e.converts()) converts++;
		h.assertTrue(converts == 2, "two conversions: " + converts);
		h.succeed();
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	private static final Rotation4[] ROTATIONS = Rotation4.values();

	private static void startFour(GameTestHelper h, Blueprint bp) {
		for (int i = 0; i < 4; i++) BuildTestSupport.startBuild(h, bp, GRID[i], ROTATIONS[i]);
		h.assertTrue(BuildTestSupport.builds(h).size() == 4, "four builds started: " + BuildTestSupport.builds(h).size());
	}

	private static void advanceAll(GameTestHelper h) {
		for (BuildJob j : new ArrayList<>(BuildTestSupport.builds(h))) {
			BuildJobs.Advanced a = BuildJobs.advance(h.getLevel().getServer(), j.id(), Integer.MAX_VALUE);
			h.assertTrue(a.finished(), "build " + j.blueprintId() + " finished: " + a);
			System.out.println("[m20] advanced " + j.blueprintId() + " " + j.rotation() + ": " + a);
		}
	}

	@GameTest(environment = "villageheart-gametest:build_instant_house", structure = YARD, skyAccess = true, maxTicks = 400)
	public void instantHouseAllRotations(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		GameTestSequence seq = BuildTestSupport.layout(h, With.GRID);
		seq.thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(4));
			startFour(h, bp);
			advanceAll(h);
		}).thenWaitUntil(() -> {
			for (int i = 0; i < 4; i++) {
				Placement p = BuildTestSupport.placement(h, GRID[i], ROTATIONS[i]);
				BlockPos marker = BuildTestSupport.markerCell(h, bp, p);
				h.assertTrue(BuildTestSupport.markerStatus(h, marker) == MarkerStatus.VALID, "the marker of house " + i + " at " + marker + " is green: " + h.getBlockState(marker) + " below " + h.getBlockState(marker.south()) + " "
						+ BuildTestSupport.markerStatus(h, marker) + " " + BuildingTestSupport.reasons(h, marker).stream().map(r -> r.message()).toList());
			}
		}).thenExecute(() -> {
			int homes = 0;
			for (Buildings.BuildingView b : Buildings.valid(h.getLevel().getServer(), BuildingType.HOUSE)) {
				homes += b.verdict().orElseThrow().slots();
			}
			h.assertTrue(homes == 4, "four homes, got " + homes);
			for (int i = 0; i < 4; i++) {
				Placement p = BuildTestSupport.placement(h, GRID[i], ROTATIONS[i]);
				BlockPos lower = BuildTestSupport.cell(h, bp, p, 3, 1, 1);
				BlockState dl = h.getBlockState(lower), du = h.getBlockState(lower.above());
				h.assertTrue(dl.getBlock() instanceof DoorBlock && dl.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, "lower door half in rotation " + i);
				h.assertTrue(du.getBlock() instanceof DoorBlock && du.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, "upper door half in rotation " + i);
				BlockState foot = h.getBlockState(BuildTestSupport.cell(h, bp, p, 2, 1, 6));
				BlockState head = h.getBlockState(BuildTestSupport.cell(h, bp, p, 1, 1, 6));
				h.assertTrue(foot.getBlock() instanceof BedBlock && foot.getValue(BedBlock.PART) == BedPart.FOOT, "bed foot in rotation " + i);
				h.assertTrue(head.getBlock() instanceof BedBlock && head.getValue(BedBlock.PART) == BedPart.HEAD, "bed head in rotation " + i);
				h.assertTrue(h.getBlockState(BuildTestSupport.cell(h, bp, p, 4, 2, 6)).is(Blocks.WALL_TORCH), "wall torch in rotation " + i);
			}
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "no build left");
			VillageTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:build_instant_shed", structure = YARD, skyAccess = true, maxTicks = 400)
	public void instantShedAllRotations(GameTestHelper h) {
		Blueprint bp = Blueprints.STORAGE_SHED;
		long[] before = new long[2];
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.shedMaterials(4));
			var snap = Stockpiles.snapshot(h.getLevel().getServer());
			before[0] = snap.sites();
			before[1] = snap.containers();
			startFour(h, bp);
			advanceAll(h);
		}).thenWaitUntil(() -> {
			var snap = Stockpiles.snapshot(h.getLevel().getServer());
			h.assertTrue(snap.sites() == before[0] + 4, "four more Storage sites: " + snap.sites() + " from " + before[0]);
			h.assertTrue(snap.containers() == before[1] + 8, "eight more containers: " + snap.containers() + " from " + before[1]);
		}).thenExecute(() -> {
			for (int i = 0; i < 4; i++) {
				Placement p = BuildTestSupport.placement(h, GRID[i], ROTATIONS[i]);
				BlockPos marker = BuildTestSupport.markerCell(h, bp, p);
				h.assertTrue(BuildTestSupport.markerStatus(h, marker) == MarkerStatus.VALID, "the marker of shed " + i + " is green: "
						+ BuildTestSupport.markerStatus(h, marker) + " " + BuildingTestSupport.reasons(h, marker).stream().map(r -> r.message()).toList());
				BlockState c1 = h.getBlockState(BuildTestSupport.cell(h, bp, p, 1, 1, 6)), c2 = h.getBlockState(BuildTestSupport.cell(h, bp, p, 5, 1, 6));
				h.assertTrue(c1.is(Blocks.CHEST) && c2.is(Blocks.CHEST), "two chests in rotation " + i);
				h.assertTrue(c1.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE
						&& c2.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE,
						"the chests did not merge in rotation " + i);
				// the chests face into the room: the front of the chest is the open cell next to it
				Dir toRoom = ROTATIONS[i].apply(Dir.NORTH);
				h.assertTrue(c1.getValue(net.minecraft.world.level.block.ChestBlock.FACING).name().equals(toRoom.name()), "the chest faces the room in rotation " + i);
			}
			VillageTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:build_instant_farm", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void instantFarmAllRotations(GameTestHelper h) {
		Blueprint bp = Blueprints.FARM_PLOT;
		GameTestSequence seq = BuildTestSupport.layout(h, With.GRID);
		seq.thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.farmMaterials(4));
			startFour(h, bp);
			advanceAll(h);
		}).thenExecute(() -> {
			for (int i = 0; i < 4; i++) {
				Placement p = BuildTestSupport.placement(h, GRID[i], ROTATIONS[i]);
				checkFarm(h, bp, p, i);
			}
		}).thenIdle(800).thenExecute(() -> {
			// placed dry (moisture 0): the water makes it moist on its random ticks
			for (int i = 0; i < 4; i++) {
				Placement p = BuildTestSupport.placement(h, GRID[i], ROTATIONS[i]);
				for (int z = 1; z <= 9; z++) for (int x = 1; x <= 9; x++) {
					BlockState s = h.getBlockState(BuildTestSupport.cell(h, bp, p, x, 0, z));
					if (s.is(Blocks.WATER)) continue;
					h.assertTrue(s.is(Blocks.FARMLAND), "farmland stays farmland at (" + x + ", " + z + ") of plot " + i + ": " + s);
					h.assertTrue(s.getValue(FarmBlock.MOISTURE) == 7, "moisture 7 at (" + x + ", " + z + ") of plot " + i + ": " + s.getValue(FarmBlock.MOISTURE));
				}
			}
			VillageTestSupport.done(h);
		});
	}

	private static void checkFarm(GameTestHelper h, Blueprint bp, Placement p, int i) {
		int farmland = 0, water = 0;
		for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			boolean ring = x == 0 || x == bp.width() - 1 || z == 0 || z == bp.depth() - 1;
			BlockPos g = BuildTestSupport.cell(h, bp, p, x, 0, z);
			BlockPos f = BuildTestSupport.cell(h, bp, p, x, 1, z);
			BlockState gs = h.getBlockState(g), fs = h.getBlockState(f);
			if (ring) {
				boolean gate = x == 5 && z == 0;
				if (gate) {
					h.assertTrue(fs.getBlock() instanceof FenceGateBlock, "the gate stands at the front of plot " + i + ": " + fs);
					Dir front = p.front(bp);
					h.assertTrue(fs.getValue(FenceGateBlock.FACING).name().equals(front.name()), "the gate faces the front in plot " + i);
				} else {
					h.assertTrue(fs.getBlock() instanceof net.minecraft.world.level.block.FenceBlock, "a fence in the ring at (" + x + ", " + z + ") of plot " + i + ": " + fs);
					// connected to its ring neighbours: the side property towards a ring neighbour is true
					for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
						BlockState n = h.getBlockState(f.relative(d));
						boolean ringNeighbour = n.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || n.getBlock() instanceof FenceGateBlock;
						if (ringNeighbour) {
							h.assertTrue(fs.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d)), "fence at (" + x + ", " + z + ") of plot " + i + " connects " + d);
						}
					}
				}
			} else {
				if (gs.is(Blocks.WATER)) water++;
				else {
					h.assertTrue(gs.is(Blocks.FARMLAND), "farmland at (" + x + ", " + z + ") of plot " + i + ": " + gs);
					farmland++;
				}
				h.assertTrue(fs.isAir(), "air above the farmland at (" + x + ", " + z + ") of plot " + i);
			}
		}
		h.assertTrue(farmland == 80 && water == 1, "80 farmland and 1 water in plot " + i + ": " + farmland + "/" + water);
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_clearing", structure = YARD, skyAccess = true, maxTicks = 600)
	public void clearingDropsToStockpile(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement p = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE);
		List<BlockPos> natural = new ArrayList<>();
		BlockPos persistent = BuildTestSupport.cell(h, bp, p, 3, 2, 3);
		Map<String, Long> before = new HashMap<>();
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			var level = h.getLevel();
			h.assertTrue(level.getGameRules().get(GameRules.RANDOM_TICK_SPEED) == 0, "random ticks are off in this environment");
			// 3 dirt (one with short grass on it), 2 stone, 2 natural leaves and a player's leaf block, all in touched cells
			BlockPos[] dirt = {BuildTestSupport.cell(h, bp, p, 1, 0, 2), BuildTestSupport.cell(h, bp, p, 2, 0, 2), BuildTestSupport.cell(h, bp, p, 3, 0, 2)};
			for (BlockPos d : dirt) { h.setBlock(d, Blocks.DIRT); natural.add(d); }
			h.setBlock(dirt[0].above(), Blocks.SHORT_GRASS);
			natural.add(dirt[0].above());
			BlockPos[] stone = {BuildTestSupport.cell(h, bp, p, 4, 0, 3), BuildTestSupport.cell(h, bp, p, 5, 1, 3)};
			for (BlockPos s : stone) { h.setBlock(s, Blocks.STONE); natural.add(s); }
			BlockPos[] leaves = {BuildTestSupport.cell(h, bp, p, 2, 3, 3), BuildTestSupport.cell(h, bp, p, 4, 3, 4)};
			for (BlockPos l : leaves) {
				h.setBlock(l, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
				natural.add(l);
			}
			h.setBlock(persistent, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			var server = level.getServer();
			var problem = SiteValidation.check(level, VillageClaims.current(server).orElseThrow(), bp, p, Rank.OWNER);
			h.assertTrue(problem != null && problem.code() == SiteRules.Code.BLOCKED, "the persistent leaf blocks the site: " + (problem == null ? "ok" : problem.english()));
			Pos at = new Pos(h.absolutePos(persistent).getX(), h.absolutePos(persistent).getY(), h.absolutePos(persistent).getZ());
			h.assertTrue(problem.english().equals("oak_leaves in the way at " + at), "the refusal names the block and the cell: " + problem.english());
			h.setBlock(persistent, Blocks.AIR);
			problem = SiteValidation.check(level, VillageClaims.current(server).orElseThrow(), bp, p, Rank.OWNER);
			h.assertTrue(problem == null, "the site is fine once the player's block is gone: " + (problem == null ? "" : problem.english()));
			before.putAll(Stockpiles.totalsById(server));
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			BuildJobs.Advanced a = BuildJobs.advance(server, BuildTestSupport.onlyBuild(h), 246);
			h.assertTrue(a.steps() == 246 && !a.finished(), "the 246 clear steps ran: " + a);
			for (Pos t : SiteRules.touched(bp, p)) {
				h.assertTrue(h.getBlockState(BuildTestSupport.rel(h, t)).isAir(), "cleared: " + t);
			}
			Map<String, Long> after = Stockpiles.totalsById(server);
			Set<String> random = Set.of("minecraft:wheat_seeds", "minecraft:stick", "minecraft:oak_sapling", "minecraft:apple");
			for (String id : new TreeSet<>(union(before, after))) {
				long d = after.getOrDefault(id, 0L) - before.getOrDefault(id, 0L);
				if (id.equals("minecraft:dirt")) h.assertTrue(d == 3, "dirt +3, got " + d);
				else if (id.equals("minecraft:cobblestone")) h.assertTrue(d == 2, "cobblestone +2, got " + d);
				else if (!random.contains(id)) h.assertTrue(d == 0, id + " unchanged, got " + d);
			}
			// without drops nothing is gained
			BuildJobs.cancel(server, BuildTestSupport.onlyBuild(h));
			h.getLevel().getGameRules().set(GameRules.BLOCK_DROPS, false, server);
			for (BlockPos n : natural) h.setBlock(n, Blocks.DIRT);
			Map<String, Long> mid = Stockpiles.totalsById(server);
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			BuildJobs.advance(server, BuildTestSupport.onlyBuild(h), 246);
			for (BlockPos n : natural) h.assertTrue(h.getBlockState(n).isAir(), "cleared without drops: " + n);
			h.assertTrue(Stockpiles.totalsById(server).equals(mid), "block_drops off: nothing gained");
			VillageTestSupport.done(h);
		});
	}

	private static Set<String> union(Map<String, Long> a, Map<String, Long> b) {
		Set<String> s = new TreeSet<>(a.keySet());
		s.addAll(b.keySet());
		return s;
	}

	/** A player-placed block is never cleared: the classification itself. */
	@GameTest(maxTicks = 100)
	public void clearableClassification(GameTestHelper h) {
		h.assertTrue(LevelSiteView.clearable(Blocks.GRASS_BLOCK.defaultBlockState()), "grass is natural");
		h.assertTrue(LevelSiteView.clearable(Blocks.STONE.defaultBlockState()), "stone is natural");
		h.assertTrue(LevelSiteView.clearable(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)), "wild leaves are natural");
		h.assertTrue(!LevelSiteView.clearable(Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)), "placed leaves are not");
		h.assertTrue(!LevelSiteView.clearable(Blocks.OAK_PLANKS.defaultBlockState()), "planks are not");
		h.assertTrue(!LevelSiteView.clearable(Blocks.CHEST.defaultBlockState()), "a chest has a block entity");
		h.assertTrue(!LevelSiteView.clearable(Blocks.GLASS.defaultBlockState()), "glass is not");
		h.assertTrue(!LevelSiteView.clearable(Blocks.LIGHT.defaultBlockState()), "light blocks are not");
		h.assertTrue(!LevelSiteView.clearable(Blocks.WHEAT.defaultBlockState()), "crops are not");
		h.assertTrue(!LevelSiteView.clearable(Blocks.FARMLAND.defaultBlockState()), "farmland is not");
		h.assertTrue(LevelSiteView.classify(Blocks.OAK_LOG.defaultBlockState()) == SiteRules.SiteCell.LOG, "a natural log is a tree");
		h.assertTrue(LevelSiteView.classify(Blocks.WATER.defaultBlockState()) == SiteRules.SiteCell.FLUID, "water is a fluid");
		h.assertTrue(LevelSiteView.classify(Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true)) == SiteRules.SiteCell.FLUID,
				"waterlogged blocks hold a fluid");
		h.succeed();
	}
}
