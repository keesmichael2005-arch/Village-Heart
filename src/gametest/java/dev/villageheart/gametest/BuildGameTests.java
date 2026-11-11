package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.GRID;
import static dev.villageheart.gametest.BuildTestSupport.YARD;
import static dev.villageheart.gametest.BuildTestSupport.env;

import com.mojang.serialization.JsonOps;
import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.build.BuildersTableMenu;
import dev.villageheart.build.net.CancelBuildPayload;
import dev.villageheart.build.net.TakeBlueprintPayload;
import dev.villageheart.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import dev.villageheart.build.BuildRuntime;
import dev.villageheart.build.BuildSystem;
import dev.villageheart.build.BlueprintItem;
import dev.villageheart.build.BlueprintSite;
import dev.villageheart.build.ModComponents;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.LevelSiteView;
import dev.villageheart.build.PaletteStates;
import dev.villageheart.build.SiteValidation;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageCodecs;
import dev.villageheart.stock.StackKey;
import java.util.UUID;
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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
			// at random tick speed 100 farmland without water would have dried out and turned to dirt: the water keeps it moist
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

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_item_flow", structure = YARD, skyAccess = true, maxTicks = 400)
	public void blueprintItemFlow(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		BuildingTestSupport.RecordingPlayer[] steward = new BuildingTestSupport.RecordingPlayer[1];
		ItemStack[] stack = new ItemStack[1];
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			steward[0] = BuildTestSupport.playerWithRank(h, "steward", Rank.STEWARD);
			stack[0] = BlueprintItem.of(bp);
			BlockPos ground = BuildTestSupport.GROUND;
			// 1: the first click shows the footprint, the front towards the player (who stands south and looks north)
			BuildingTestSupport.rightClick(h, steward[0], ground, Direction.UP, stack[0]);
			BlueprintSite site = stack[0].get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(site != null, "the first click stores a site");
			h.assertTrue(site.ground().equals(h.absolutePos(ground)), "the site is at the clicked ground: " + site.ground());
			h.assertTrue(site.rotationEnum() == Rotation4.CLOCKWISE_180, "the door faces the player: " + site.rotationEnum());
			h.assertTrue(site.ok(), "the site is fine: " + site.reason().english());
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "nothing is built by showing the site");
			// 2: sneak-click turns it a quarter, the box is checked again
			steward[0].setShiftKeyDown(true);
			BuildingTestSupport.rightClick(h, steward[0], ground, Direction.UP, stack[0]);
			steward[0].setShiftKeyDown(false);
			site = stack[0].get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(site.rotationEnum() == Rotation4.COUNTERCLOCKWISE_90, "a sneak-click rotates: " + site.rotationEnum());
			h.assertTrue(site.ground().equals(h.absolutePos(ground)) && site.ok(), "the same ground, still fine");
			steward[0].setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack[0]);
			h.assertTrue(dev.villageheart.build.BlueprintUse.tryMirror(steward[0]), "swap-hands mirrors");
			stack[0] = steward[0].getMainHandItem();
			site = stack[0].get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(site.mirrored() && site.rotationEnum() == Rotation4.COUNTERCLOCKWISE_90, "mirror keeps the rotation: " + site);
			h.assertTrue(dev.villageheart.build.BlueprintUse.tryMirror(steward[0]), "swap-hands mirrors back");
			stack[0] = steward[0].getMainHandItem();
			site = stack[0].get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(!site.mirrored() && site.ok(), "mirror toggles off and the site is still fine");
			// 3: a click outside the box moves the site (and picks the facing again)
			BlockPos other = new BlockPos(12, -1, 12);
			BuildingTestSupport.rightClick(h, steward[0], other, Direction.UP, stack[0]);
			site = stack[0].get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(site.ground().equals(h.absolutePos(other)) && site.rotationEnum() == Rotation4.CLOCKWISE_180, "a click outside moves the site: " + site);
			BuildingTestSupport.rightClick(h, steward[0], ground, Direction.UP, stack[0]);
			site = stack[0].get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(site.ground().equals(h.absolutePos(ground)), "and back");
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "still nothing built");
			// 4: a click on the same ground block again (one below the box) confirms
			BuildingTestSupport.rightClick(h, steward[0], ground, Direction.UP, stack[0]);
			List<BuildJob> builds = BuildTestSupport.builds(h);
			h.assertTrue(builds.size() == 1, "the second click confirms: " + builds.size() + " builds");
			h.assertTrue(builds.get(0).rotation() == Rotation4.CLOCKWISE_180, "with the shown rotation: " + builds.get(0).rotation());
			h.assertTrue(builds.get(0).ground().x() == h.absolutePos(ground).getX() && builds.get(0).blueprintId().equals("small_house"), "at the shown site");
			h.assertTrue(stack[0].isEmpty(), "the blueprint is used up");
			h.assertTrue(steward[0].messages.size() == 2, "two chat lines: " + steward[0].messages.size());
			var set = BuildingTestSupport.translatable(steward[0].messages.get(0));
			h.assertTrue(set.getKey().equals("build.villageheart.msg.site_set"), "the site is named in chat: " + set.getKey());
			h.assertTrue(BuildTestSupport.lastKey(h, steward[0]).equals("build.villageheart.msg.no_builder"), "and there is no builder yet");
			// 5: a Resident may look but not confirm
			var resident = BuildTestSupport.playerWithRank(h, "resident", Rank.RESIDENT);
			ItemStack rs = BlueprintItem.of(bp);
			BuildingTestSupport.rightClick(h, resident, BuildTestSupport.GROUND, Direction.UP, rs);
			BlueprintSite rsite = rs.get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(rsite != null && !rsite.ok(), "a Resident's site is not ok");
			h.assertTrue(rsite.reason().english().equals("Only a Steward or the Owner can start a blueprint"), "with the rank text: " + rsite.reason().english());
			BuildingTestSupport.rightClick(h, resident, BuildTestSupport.GROUND, Direction.UP, rs);
			h.assertTrue(BuildTestSupport.builds(h).size() == 1, "a Resident's confirm creates nothing");
			h.assertTrue(!rs.isEmpty(), "and keeps the blueprint");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_site_refusals", structure = YARD, skyAccess = true, maxTicks = 600)
	public void siteRefusals(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		BlockPos houseOrigin = new BlockPos(2, 0, 14);
		BlockPos houseMarker = BuildingTestSupport.markerPos(houseOrigin, 5);
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			// a valid House (bed and a light) on the west side and, unmarked, a 3x3x3 cobblestone block of a player on the standard site
			BuildingTestSupport.room(h, houseOrigin, 5, 4);
			BlockPos bed = BuildingTestSupport.inside(houseOrigin, 0, 0, 3);
			h.setBlock(bed, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, net.minecraft.core.Direction.WEST));
			h.setBlock(bed.east(), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, net.minecraft.core.Direction.WEST));
			h.setBlock(BuildingTestSupport.inside(houseOrigin, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, houseMarker, net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, houseMarker, MarkerStatus.VALID)).thenExecute(() -> {
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			// a player's 3x3x3 cobblestone block on the standard site, water next to the site at (20, 12), a natural oak on the site at (25, 24)
			for (int x = 14; x <= 16; x++) for (int y = 0; y <= 2; y++) for (int z = 18; z <= 20; z++) h.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE);
			h.setBlock(new BlockPos(16, 0, 12), Blocks.WATER);
			LumberTestSupport.growOak(h, new BlockPos(25, 0, 24), 1L);
			int before = BuildTestSupport.worldHash(h);
			var level = h.getLevel();
			// 1: over the valid room's wall, and over its interior
			var wall = SiteValidation.check(level, village, bp, BuildTestSupport.placement(h, new BlockPos(11, -1, 17), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(wall != null && wall.code() == SiteRules.Code.OVERLAP && wall.english().startsWith("Overlaps the House at ("), "over the wall: "
					+ (wall == null ? "ok" : wall.english()));
			var inside = SiteValidation.check(level, village, bp, BuildTestSupport.placement(h, new BlockPos(6, -1, 17), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(inside != null && inside.code() == SiteRules.Code.OVERLAP, "over the interior: " + (inside == null ? "ok" : inside.english()));
			// 2: over a player's unmarked cobblestone
			var hut = SiteValidation.check(level, village, bp, BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE), Rank.OWNER);
			h.assertTrue(hut != null && hut.code() == SiteRules.Code.BLOCKED && hut.english().startsWith("cobblestone in the way at ("), "over the unmarked hut: "
					+ (hut == null ? "ok" : hut.english()));
			// 3: 81 blocks from the Heart
			var heart = village.heart();
			var far = SiteValidation.check(level, village, bp, new Placement(new Pos(heart.x() + 78, heart.y() - 3, heart.z()), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(far != null && far.code() == SiteRules.Code.OUTSIDE && far.english().startsWith("Outside the village: 8"), "81 blocks away: "
					+ (far == null ? "ok" : far.english()));
			// 4: Hamlet tier
			var hamlet = SiteValidation.check(level, village.withTier(dev.villageheart.core.Tier.HAMLET), bp,
					BuildTestSupport.placement(h, new BlockPos(20, -1, 25), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(hamlet != null && hamlet.code() == SiteRules.Code.TIER && hamlet.english().equals("Requires Village tier"), "at Hamlet tier: "
					+ (hamlet == null ? "ok" : hamlet.english()));
			// 5: a natural oak in the site
			var tree = SiteValidation.check(level, village, bp, BuildTestSupport.placement(h, new BlockPos(25, -1, 24), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(tree != null && tree.code() == SiteRules.Code.TREE && tree.english().startsWith("Tree in the way at ("), "a natural oak: "
					+ (tree == null ? "ok" : tree.english()));
			// 6: water next to the site
			var wet = SiteValidation.check(level, village, bp, BuildTestSupport.placement(h, new BlockPos(20, -1, 12), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(wet != null && wet.code() == SiteRules.Code.FLUID && wet.english().startsWith("Water or lava at ("), "next to water: "
					+ (wet == null ? "ok" : wet.english()));
			// in every case no build exists and validation changed no block
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "no check started a build");
			h.assertTrue(BuildTestSupport.worldHash(h) == before, "validation changed the world");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_reservations", structure = YARD, skyAccess = true, maxTicks = 600)
	public void reservationsHoldMaterials(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		StackKey planks = StackKey.of(Items.OAK_PLANKS), cobble = StackKey.of(Items.COBBLESTONE);
		UUID[] id = new UUID[1];
		GameTestSequence seq = BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			id[0] = BuildTestSupport.onlyBuild(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.BUILD_REFRESH_TICKS + 4, "the build lists what it still needs", () -> {
			var server = h.getLevel().getServer();
			var res = Stockpiles.reservations(server);
			h.assertTrue(res.reservedBy(id[0], planks) == 0, "a build does not reserve planks: " + res.reservedBy(id[0], planks));
			h.assertTrue(res.reservedBy(id[0], cobble) == 0, "a build does not reserve cobblestone");
			h.assertTrue(BuildSystem.demanded(server, planks) == 119, "the house still needs 119 planks: " + BuildSystem.demanded(server, planks));
			h.assertTrue(BuildSystem.demanded(server, cobble) == 49, "the house still needs 49 cobblestone");
		});
		seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			// not a lock: another consumer can take the planks. The market still will not sell them.
			ItemStack got = Stockpiles.withdraw(h.getLevel(), null, planks, 64, null);
			h.assertTrue(got.getCount() == 64, "another consumer can take planks: " + got.getCount());
			long free = -1;
			long count = -1;
			for (var item : dev.villageheart.market.Markets.stockItems(server)) {
				if (item.key().equals(cobble)) {
					free = item.free();
					count = item.count();
				}
			}
			h.assertTrue(count == 49 && free == 0, "the market will not sell cobblestone a build still needs (count " + count + " free " + free + ")");
			h.assertTrue(BuildJobs.cancel(server, id[0]), "cancelled");
			h.assertTrue(BuildSystem.demanded(server, planks) == 0, "a cancelled build demands nothing");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-7 (roadmap) ------------------------------------------------------------------------

	private static final int TWO_DAYS = 48000;

	private static void assertExactDeduction(GameTestHelper h, Map<String, Long> before, Map<String, Long> expectedAfter, String what) {
		Map<String, Long> after = Stockpiles.totalsById(h.getLevel().getServer());
		h.assertTrue(after.equals(expectedAfter), what + ": expected " + expectedAfter + ", got " + after + " (before " + before + ")");
	}

	private static void assertNoItems(GameTestHelper h, Item... items) {
		for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(-2, -3, -2)).getX(), h.absolutePos(new BlockPos(-2, -3, -2)).getY(),
						h.absolutePos(new BlockPos(-2, -3, -2)).getZ(), h.absolutePos(new BlockPos(34, 30, 34)).getX(),
						h.absolutePos(new BlockPos(34, 30, 34)).getY(), h.absolutePos(new BlockPos(34, 30, 34)).getZ()))) {
			for (Item i : items) h.assertTrue(!e.getItem().is(i), "an item entity of " + i + " lies in the yard");
		}
	}

	@GameTest(environment = "villageheart-gametest:build_house_two_days", structure = YARD, skyAccess = true, maxTicks = 50000)
	public void smallHouseWithinTwoDays(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		long[] t0 = new long[1];
		long[] placedBefore = new long[1];
		Map<String, Long> before = new HashMap<>();
		var builder = new net.minecraft.world.entity.npc.villager.Villager[1];
		var log = new BuildTestSupport.ActionLog[1];
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			before.putAll(Stockpiles.totalsById(h.getLevel().getServer()));
			log[0] = BuildTestSupport.watchActions();
			builder[0] = BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			t0[0] = h.getTick();
			placedBefore[0] = dev.villageheart.worker.job.WorkStats.get("builder", "blocks_placed");
		}).thenIdle(5).thenExecute(() -> {
			h.assertTrue(h.getLevel().getDayTime() > 1000, "the day time advances");
			h.assertTrue(dev.villageheart.worker.WorkerAttachments.get(builder[0]).orElseThrow().needs().speed() == 1.0, "the builder works at full speed");
		}).thenWaitUntil(() -> BuildTestSupport.assertHouseGreen(h, bp, pl)).thenExecute(() -> {
			long ticks = h.getTick() - t0[0];
			System.out.println("[m20] small house finished after " + ticks + " ticks: " + log[0].places + " places, " + log[0].clears + " clears");
			h.assertTrue(ticks < TWO_DAYS, "the House is green before two days: " + ticks);
			var server = h.getLevel().getServer();
			h.assertTrue(BuildingTestSupport.record(h, BuildTestSupport.markerCell(h, bp, pl)).orElseThrow().status() == dev.villageheart.core.building.BuildingStatus.VALID,
					"the House record is valid");
			int homes = 0;
			for (var b : Buildings.valid(server, BuildingType.HOUSE)) homes += b.verdict().orElseThrow().slots();
			h.assertTrue(homes == 1, "one home: " + homes);
			assertExactDeduction(h, before, Map.of(), "the materials are deducted exactly and nothing else changed");
			assertNoItems(h, Items.OAK_PLANKS, Items.COBBLESTONE, Items.OAK_DOOR, Items.WHITE_BED, Items.TORCH);
			h.assertTrue(builder[0].getInventory().isEmpty(), "the builder carries nothing: " + builder[0].getInventory());
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "the build is done");
			h.assertTrue(log[0].places == 172, "172 placements: " + log[0].places);
			// M15: a builder earns 0.2 XP per block placed (milli-XP sums exactly), no XP for clearing
			long placed = dev.villageheart.worker.job.WorkStats.get("builder", "blocks_placed") - placedBefore[0];
			var data = dev.villageheart.worker.WorkerAttachments.get(builder[0]).orElseThrow();
			h.assertTrue(data.xp() == placed / 5, "builder XP " + data.xp() + " for " + placed + " blocks placed (expected " + placed / 5 + ")");
			h.assertTrue(data.xp() >= 34, "at least 34 XP for the House: " + data.xp());
			h.assertTrue(log[0].insideBox == 0, "every action was taken from outside the box: " + log[0].firstInside);
			dev.villageheart.worker.build.BuilderJob.listener = null;
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-8 (roadmap) ------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_pauses", structure = YARD, skyAccess = true, maxTicks = 40000)
	public void pausesAndResumes(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		Map<String, Long> before = new HashMap<>();
		int[] cursor0 = new int[1];
		int[] hash0 = new int[1];
		var builder = new net.minecraft.world.entity.npc.villager.Villager[1];
		long[] t0 = new long[1];
		WorkerTestSupport.begin(h);
		GameTestSequence seq = BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			ItemStack[] all = BuildTestSupport.houseMaterials(1);
			for (ItemStack s : all) if (s.is(Items.OAK_PLANKS)) s.setCount(107);
			BuildTestSupport.stock(h, all);
			before.putAll(Stockpiles.totalsById(h.getLevel().getServer()));
			builder[0] = BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
		});
		// the build stops by itself and says what it needs
		seq = seq.thenWaitUntil(() -> {
			var server = h.getLevel().getServer();
			var view = BuildSystem.views(server).stream().findFirst().orElseThrow();
			h.assertTrue(view.status() == dev.villageheart.core.blueprint.BuildStatus.NEEDS_MATERIALS, "the build is waiting for materials: " + view.status());
			h.assertTrue(view.line().english().equals("Needs 12 more planks"), "the build says what it needs: " + view.line().english());
			h.assertTrue(view.line().key().equals("build.villageheart.needs") && view.line().args().equals(List.of("12", "planks")), "key and args: " + view.line());
			var rec = VillageClaims.current(server).orElseThrow().worker(builder[0].getUUID()).orElseThrow();
			h.assertTrue(dev.villageheart.worker.Workers.status(server, rec) == dev.villageheart.core.worker.WorkerStatus.NEEDS_MATERIALS,
					"the builder's status: " + dev.villageheart.worker.Workers.status(server, rec));
		});
		seq = seq.thenExecute(() -> {
			cursor0[0] = BuildTestSupport.cursor(h);
			hash0[0] = BuildTestSupport.boxHash(h, bp, pl);
			t0[0] = h.getTick();
			System.out.println("[m20] paused at cursor " + cursor0[0] + " after the House was built up to its last 12 roof blocks");
		}).thenIdle(600).thenExecute(() -> {
			h.assertTrue(BuildTestSupport.cursor(h) == cursor0[0], "the cursor did not move for 600 ticks: " + cursor0[0] + " -> " + BuildTestSupport.cursor(h));
			h.assertTrue(BuildTestSupport.boxHash(h, bp, pl) == hash0[0], "no site block changed while waiting");
			// the planks arrive; the evening must not catch the resume
			h.getLevel().setDayTime(2000);
			BuildTestSupport.stock(h, new ItemStack(Items.OAK_PLANKS, 12));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.BUILD_REFRESH_TICKS + 600, "the build resumes", () ->
				h.assertTrue(BuildTestSupport.cursor(h) > cursor0[0] || BuildTestSupport.cursor(h) == -1, "the cursor moves"));
		seq.thenWaitUntil(() -> BuildTestSupport.assertHouseGreen(h, bp, pl)).thenExecute(() -> {
			assertExactDeduction(h, before, Map.of(), "all 119 planks and every other material, exactly");
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "the build is done");
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_restart_firing", structure = YARD, skyAccess = true, maxTicks = 50000)
	public void buildSurvivesRestartAndFiring(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		Map<String, Long> before = new HashMap<>();
		var builder = new net.minecraft.world.entity.npc.villager.Villager[1];
		int[] restartCursor = new int[1];
		long[] carriedBefore = new long[2];
		UUID[] id = new UUID[1];
		StackKey planks = StackKey.of(Items.OAK_PLANKS);
		WorkerTestSupport.begin(h);
		GameTestSequence seq = BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			before.putAll(Stockpiles.totalsById(h.getLevel().getServer()));
			builder[0] = BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			id[0] = BuildTestSupport.onlyBuild(h);
		});
		// (1) a restart once the floor is down: save, load, install the loaded state, forget every transient thing
		seq = seq.thenWaitUntil(() -> h.assertTrue(BuildTestSupport.cursor(h) >= 246 + 49 + 10, "the floor is down: " + BuildTestSupport.cursor(h)));
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			VillageState live = VillageClaims.current(server).orElseThrow();
			VillageState decoded = VillageCodecs.VILLAGE_STATE.parse(JsonOps.INSTANCE, VillageCodecs.VILLAGE_STATE.encodeStart(JsonOps.INSTANCE, live).getOrThrow()).getOrThrow();
			h.assertTrue(decoded.equals(live), "the saved village reads back equal");
			VillageClaims.data(server).update(v -> decoded);
			BuildRuntime.resetForTests();
			Stockpiles.resetRuntimeForTests();
			restartCursor[0] = BuildTestSupport.cursor(h);
			h.assertTrue(restartCursor[0] >= 305, "the build is where it was: " + restartCursor[0]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, Balance.BUILD_REFRESH_TICKS + 200, "the build goes on after the restart", () -> {
			var res = Stockpiles.reservations(h.getLevel().getServer());
			int cursor = BuildTestSupport.cursor(h);
			h.assertTrue(cursor > restartCursor[0] || BuildTestSupport.builds(h).isEmpty(),
					"the cursor moves on from " + restartCursor[0] + ": " + cursor);
			h.assertTrue(res.reservedBy(id[0], planks) == 0, "a build does not reserve planks");
		});
		// (2) the builder is fired while it carries planks
		seq = seq.thenWaitUntil(() -> {
			var job = BuildTestSupport.builds(h).get(0);
			h.assertTrue(job.carriedOf("minecraft:oak_planks") > 0 && builder[0].getUUID().equals(job.carrier()), "the builder carries planks: " + job.carried());
		});
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			var job = BuildTestSupport.builds(h).get(0);
			carriedBefore[0] = job.carriedOf("minecraft:oak_planks");
			carriedBefore[1] = Stockpiles.count(server, Items.OAK_PLANKS);
			h.assertTrue(dev.villageheart.worker.Workers.fire(h.getLevel(), builder[0]), "the builder was fired");
			h.assertTrue(Stockpiles.count(server, Items.OAK_PLANKS) == carriedBefore[1] + carriedBefore[0],
					"the carried planks went back to Storage: " + carriedBefore[1] + " + " + carriedBefore[0] + " -> " + Stockpiles.count(server, Items.OAK_PLANKS));
			var after = BuildTestSupport.builds(h).get(0);
			h.assertTrue(after.carried().isEmpty() && after.carrier() == null, "nothing is carried for the build any more: " + after.carried());
			builder[0] = BuildTestSupport.hireBuilder(h);
		});
		seq.thenWaitUntil(() -> BuildTestSupport.assertHouseGreen(h, bp, pl)).thenExecute(() -> {
			assertExactDeduction(h, before, Map.of(), "the total deduction is still exact");
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "the build is done");
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	private static BuildingTestSupport.RecordingPlayer atTable(GameTestHelper h, String name, Rank rank) {
		var p = BuildTestSupport.playerWithRank(h, name, rank);
		var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(BuildingTestSupport.inside(BuildTestSupport.HUT, 1, 0, 1)));
		p.setPos(at.x, at.y, at.z);
		return p;
	}

	private static BuildersTableMenu openTable(GameTestHelper h, ServerPlayer p) {
		BuildingTestSupport.rightClick(h, p, BuildTestSupport.table(), Direction.UP, ItemStack.EMPTY);
		h.assertTrue(p.containerMenu instanceof BuildersTableMenu, p.getName().getString() + " has the Builder's Table open");
		return (BuildersTableMenu) p.containerMenu;
	}

	@GameTest(environment = "villageheart-gametest:build_table_permissions", structure = YARD, skyAccess = true, maxTicks = 600)
	public void buildersTablePermissions(GameTestHelper h) {
		var sent = HeartTestSupport.recorder();
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			var visitor = atTable(h, "visitor", Rank.VISITOR);
			var resident = atTable(h, "resident", Rank.RESIDENT);
			var steward = atTable(h, "steward", Rank.STEWARD);
			Item bpItem = ModItems.BLUEPRINT;
			// everybody in the claim may open the table and read it
			for (var p : List.of(visitor, resident, steward)) {
				var m = openTable(h, p);
				h.assertTrue(m.snapshot().blueprints().size() == Blueprints.all().size(), p.getName().getString() + " sees every blueprint");
				h.assertTrue(m.snapshot().viewerRank() == VillageClaims.current(h.getLevel().getServer()).orElseThrow().rankOf(p.getUUID()), "with the viewer's rank");
				h.assertTrue(!m.snapshot().blueprints().get(0).materials().isEmpty(), "and the material list");
				h.assertTrue(!m.snapshot().blueprints().get(0).locked(), "unlocked at Village tier");
			}
			// the list shows what Storage could give (nothing here) and what it costs
			var first = ((BuildersTableMenu) steward.containerMenu).snapshot().blueprints().get(0);
			h.assertTrue(first.id().equals("farm_plot") && first.materials().get(0).name().equals("dirt") && first.materials().get(0).needed() == 80
					&& first.materials().get(0).have() == 0, "dirt 0 / 80: " + first.materials());
			// Take: a Visitor and a Resident are refused and get nothing
			for (var p : List.of(visitor, resident)) {
				HeartTestSupport.send(p, TakeBlueprintPayload.CODEC, new TakeBlueprintPayload(p.containerMenu.containerId, "small_house"));
				h.assertTrue(p.getInventory().countItem(bpItem) == 0, p.getName().getString() + " got no blueprint");
				var r = HeartTestSupport.lastResult(sent, p);
				h.assertTrue(!r.ok() && r.key().equals("gui.villageheart.table.no_permission"), "refused with the rank text: " + r.key());
			}
			// a Steward gets exactly one Blueprint of that id
			HeartTestSupport.send(steward, TakeBlueprintPayload.CODEC, new TakeBlueprintPayload(steward.containerMenu.containerId, "small_house"));
			h.assertTrue(steward.getInventory().countItem(bpItem) == 1, "the Steward got one blueprint");
			ItemStack got = ItemStack.EMPTY;
			for (int i = 0; i < steward.getInventory().getContainerSize(); i++) if (steward.getInventory().getItem(i).is(bpItem)) got = steward.getInventory().getItem(i);
			h.assertTrue("small_house".equals(got.get(ModComponents.BLUEPRINT)), "of the right id");
			h.assertTrue(HeartTestSupport.lastResult(sent, steward).ok(), "and was told so");
			// an unknown id, a stale container id
			HeartTestSupport.send(steward, TakeBlueprintPayload.CODEC, new TakeBlueprintPayload(steward.containerMenu.containerId, "castle"));
			h.assertTrue(HeartTestSupport.lastResult(sent, steward).key().equals("gui.villageheart.table.unknown"), "an unknown blueprint is refused");
			HeartTestSupport.send(steward, TakeBlueprintPayload.CODEC, new TakeBlueprintPayload(steward.containerMenu.containerId + 50, "small_house"));
			h.assertTrue(HeartTestSupport.lastResult(sent, steward).key().equals("gui.villageheart.table.stale"), "a stale container id is refused");
			h.assertTrue(steward.getInventory().countItem(bpItem) == 1, "no second blueprint");
			// Hamlet tier: locked
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.HAMLET);
			HeartTestSupport.send(steward, TakeBlueprintPayload.CODEC, new TakeBlueprintPayload(steward.containerMenu.containerId, "farm_plot"));
			var locked = HeartTestSupport.lastResult(sent, steward);
			h.assertTrue(!locked.ok() && locked.key().equals("gui.villageheart.table.locked") && locked.args().equals(List.of("Village")), "locked at Hamlet tier: " + locked);
			h.assertTrue(steward.getInventory().countItem(bpItem) == 1, "nothing taken");
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.VILLAGE);
			// Cancel: a Resident is refused, a Steward cancels and the reservations go
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			BuildTestSupport.startBuild(h, Blueprints.SMALL_HOUSE, BuildTestSupport.GROUND, Rotation4.NONE);
			UUID id = BuildTestSupport.onlyBuild(h);
			StackKey planks = StackKey.of(Items.OAK_PLANKS);
			h.assertTrue(BuildSystem.demanded(h.getLevel().getServer(), planks) == 119, "the build still needs its planks");
			HeartTestSupport.send(resident, CancelBuildPayload.CODEC, new CancelBuildPayload(resident.containerMenu.containerId, id));
			h.assertTrue(BuildTestSupport.builds(h).size() == 1 && !HeartTestSupport.lastResult(sent, resident).ok(), "a Resident cannot cancel");
			HeartTestSupport.send(steward, CancelBuildPayload.CODEC, new CancelBuildPayload(steward.containerMenu.containerId + 9, id));
			h.assertTrue(BuildTestSupport.builds(h).size() == 1, "a stale container id cannot cancel");
			HeartTestSupport.send(steward, CancelBuildPayload.CODEC, new CancelBuildPayload(steward.containerMenu.containerId, id));
			h.assertTrue(BuildTestSupport.builds(h).isEmpty() && HeartTestSupport.lastResult(sent, steward).ok(), "a Steward cancels");
			h.assertTrue(BuildSystem.demanded(h.getLevel().getServer(), planks) == 0, "a cancelled build needs nothing");
			HeartTestSupport.done(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:build_table_outside", structure = YARD, skyAccess = true, maxTicks = 400)
	public void tableOutsideTheClaimDoesNotOpen(GameTestHelper h) {
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			var steward = atTable(h, "steward", Rank.STEWARD);
			var village = VillageClaims.current(h.getLevel().getServer()).orElseThrow();
			// the Heart moves far away in the saved data: the table is then outside the claim
			VillageClaims.data(h.getLevel().getServer()).update(v -> new VillageState(new dev.villageheart.core.HeartLocation(v.heart().dimension(),
					v.heart().x() + 5000, v.heart().y(), v.heart().z()), v.owner(), v.ownerName(), v.claimedAtGameTime(), v.ranks(), v.known(), v.treasury(),
					v.tier(), v.buildings(), v.workers(), v.treeTasks(), v.builds()));
			BuildingTestSupport.rightClick(h, steward, BuildTestSupport.table(), Direction.UP, ItemStack.EMPTY);
			h.assertTrue(!(steward.containerMenu instanceof BuildersTableMenu), "no menu outside the claim");
			h.assertTrue(BuildTestSupport.lastKey(h, steward).equals("build.villageheart.msg.not_in_village"), "the player is told");
			VillageClaims.data(h.getLevel().getServer()).update(v -> village);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_player_block", structure = YARD, skyAccess = true, maxTicks = 50000)
	public void playerBlockAppearsMidBuild(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		BlockPos glass = BuildTestSupport.cell(h, bp, pl, 0, 3, 3);
		Pos glassAbs = pl.world(bp, 0, 3, 3);
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
		}).thenWaitUntil(() -> h.assertTrue(BuildTestSupport.cursor(h) >= 246 + 49 + 5, "the builder is in layer 1: " + BuildTestSupport.cursor(h)))
				.thenExecute(() -> h.setBlock(glass, Blocks.GLASS))
				.thenWaitUntil(() -> {
					var view = BuildSystem.views(h.getLevel().getServer()).get(0);
					h.assertTrue(view.status() == dev.villageheart.core.blueprint.BuildStatus.BLOCKED && view.line().english().equals("glass in the way at " + glassAbs),
							"the build stops at the glass, naming block and cell " + glassAbs + ": " + view.status() + " " + view.line().english() + " claimed=" + view.claimed() + " pause=" + BuildRuntime.get(h.getLevel().getServer()).pause(view.job().id())
					+ " now=" + h.getLevel().getServer().overworld().getGameTime() + " day=" + h.getLevel().getDayTime());
				}).thenIdle(300).thenExecute(() -> {
					h.assertTrue(h.getBlockState(glass).is(Blocks.GLASS), "the glass was never broken");
					h.assertTrue(BuildTestSupport.cursor(h) > 0, "the build is still there");
					h.setBlock(glass, Blocks.AIR);
				}).thenWaitUntil(() -> BuildTestSupport.assertHouseGreen(h, bp, pl)).thenExecute(() -> {
					h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "and then it finishes");
					WorkerTestSupport.end(h);
					VillageTestSupport.done(h);
				});
	}

	// ---- review of M20 round 1: clearing must not break what a player attached to the site -------------------

	/**
	 * Natural dirt in touched cells with things of a player resting on them from outside the site (the verifier's probe: a wall torch on the west
	 * face, a standing torch above the roof layer, a ladder on the east face, an item frame on a face): the site is refused, naming the thing and
	 * its cell, and nothing is touched. Without them the same site is fine.
	 */
	@GameTest(environment = "villageheart-gametest:build_attached_refusal", structure = YARD, skyAccess = true, maxTicks = 600)
	public void attachedThingsRefuseTheSite(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement p = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE);
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			var level = h.getLevel();
			var server = level.getServer();
			BlockPos westDirt = BuildTestSupport.cell(h, bp, p, 0, 1, 3), roofDirt = BuildTestSupport.cell(h, bp, p, 3, 4, 3);
			BlockPos eastDirt = BuildTestSupport.cell(h, bp, p, 6, 2, 3), frameDirt = BuildTestSupport.cell(h, bp, p, 6, 3, 5);
			for (BlockPos d : new BlockPos[] {westDirt, roofDirt, eastDirt, frameDirt}) {
				h.assertTrue(SiteRules.touched(bp, p).contains(BuildTestSupport.abs(h, d)), "a touched cell: " + d);
				h.setBlock(d, Blocks.DIRT);
			}
			var village = VillageClaims.current(server).orElseThrow();
			h.assertTrue(SiteValidation.check(level, village, bp, p, Rank.OWNER) == null, "dirt alone is fine");
			// each thing alone: refused with its name and cell, and still there afterwards
			BlockPos wallTorch = westDirt.west(), standing = roofDirt.above(), ladder = eastDirt.east(), frameAt = frameDirt.east();
			h.setBlock(wallTorch, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.WEST));
			assertRefused(h, bp, p, "wall_torch", wallTorch);
			h.setBlock(wallTorch, Blocks.AIR);
			h.setBlock(standing, Blocks.TORCH);
			assertRefused(h, bp, p, "torch", standing);
			h.setBlock(standing, Blocks.AIR);
			// carpets ask isEmptyBlock (a default method of the level), and an ascending rail is removed by its neighbour update
			h.setBlock(standing, Blocks.WHITE_CARPET);
			assertRefused(h, bp, p, "white_carpet", standing);
			h.setBlock(standing, Blocks.MOSS_CARPET);
			assertRefused(h, bp, p, "moss_carpet", standing);
			h.setBlock(standing, Blocks.AIR);
			h.setBlock(wallTorch.below(), Blocks.STONE);
			h.setBlock(wallTorch, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.ASCENDING_EAST));
			assertRefused(h, bp, p, "rail", wallTorch);
			h.setBlock(wallTorch, Blocks.POWERED_RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.PoweredRailBlock.SHAPE, net.minecraft.world.level.block.state.properties.RailShape.ASCENDING_EAST));
			assertRefused(h, bp, p, "powered_rail", wallTorch);
			h.setBlock(wallTorch, Blocks.AIR);
			// a flat rail beside the dirt, resting on stone, does not depend on it
			h.setBlock(wallTorch, Blocks.RAIL);
			h.assertTrue(SiteValidation.check(level, VillageClaims.current(server).orElseThrow(), bp, p, Rank.OWNER) == null, "a flat rail on stone beside the site is fine");
			h.setBlock(wallTorch, Blocks.AIR);
			h.setBlock(ladder, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
			assertRefused(h, bp, p, "ladder", ladder);
			h.setBlock(ladder, Blocks.AIR);
			var frame = new net.minecraft.world.entity.decoration.ItemFrame(level, h.absolutePos(frameAt), Direction.EAST);
			level.addFreshEntity(frame);
			var problem = SiteValidation.check(level, VillageClaims.current(server).orElseThrow(), bp, p, Rank.OWNER);
			h.assertTrue(problem != null && problem.code() == SiteRules.Code.SUPPORT && problem.english().startsWith("item_frame at ("),
					"an item frame on a touched face refuses the site: " + (problem == null ? "ok" : problem.english()));
			h.assertTrue(!frame.isRemoved(), "and the frame is still there");
			frame.discard();
			h.assertTrue(SiteValidation.check(level, VillageClaims.current(server).orElseThrow(), bp, p, Rank.OWNER) == null, "fine again with all of them gone");
			// the ladder against a cell that is not cleared (a stone wall of the player's outside the box) does not refuse
			BlockPos far = BuildTestSupport.cell(h, bp, p, -3, 0, 3);
			h.setBlock(far, Blocks.STONE);
			h.setBlock(far.above(), Blocks.TORCH);
			h.assertTrue(SiteValidation.check(level, VillageClaims.current(server).orElseThrow(), bp, p, Rank.OWNER) == null, "a torch on a stone far from the site is no problem");
			VillageTestSupport.done(h);
		});
	}

	private static void assertRefused(GameTestHelper h, Blueprint bp, Placement p, String name, BlockPos at) {
		var level = h.getLevel();
		var problem = SiteValidation.check(level, VillageClaims.current(level.getServer()).orElseThrow(), bp, p, Rank.OWNER);
		Pos abs = BuildTestSupport.abs(h, at);
		h.assertTrue(problem != null && problem.code() == SiteRules.Code.SUPPORT, name + " at " + at + " refuses the site: " + (problem == null ? "ok" : problem.english()));
		h.assertTrue(problem.english().equals(name + " at " + abs + " would fall: it rests on the site"), "the refusal names it: " + problem.english());
		h.assertTrue(!h.getBlockState(at).isAir(), name + " is still there");
	}

	/**
	 * A thing attached AFTER the site was confirmed: the builder reaches the cleared cell it rests on, stops with "torch in the way at ..." and
	 * breaks nothing; when the torch is taken away it goes on.
	 */
	@GameTest(environment = "villageheart-gametest:build_attached_midbuild", structure = YARD, skyAccess = true, maxTicks = 20000)
	public void attachmentAfterConfirmBlocksTheBuilder(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		BlockPos dirt = BuildTestSupport.cell(h, bp, pl, 3, 4, 3);
		BlockPos torch = dirt.above();
		Pos torchAbs = BuildTestSupport.abs(h, torch);
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			h.setBlock(dirt, Blocks.DIRT);
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			// attached after the confirm, in the same tick: the site check passed, the builder has not acted yet
			h.setBlock(torch, Blocks.TORCH);
		}).thenWaitUntil(() -> {
			var views = BuildSystem.views(h.getLevel().getServer());
			h.assertTrue(!views.isEmpty(), "the build is listed");
			var view = views.get(0);
			h.assertTrue(view.status() == dev.villageheart.core.blueprint.BuildStatus.BLOCKED && view.line().english().equals("torch in the way at " + torchAbs),
					"the build stops at the torch: " + view.status() + " " + view.line().english());
		}).thenIdle(300).thenExecute(() -> {
			h.assertTrue(h.getBlockState(torch).is(Blocks.TORCH), "the torch was never broken");
			h.assertTrue(h.getBlockState(dirt).is(Blocks.DIRT), "and neither was the dirt it stands on");
			h.assertTrue(BuildTestSupport.cursor(h) >= 0, "the build is still there");
			h.setBlock(torch, Blocks.AIR);
		}).thenWaitUntil(() -> h.assertTrue(h.getBlockState(dirt).isAir() && BuildTestSupport.cursor(h) > 246,
				"the builder goes on and clears the dirt: cursor " + BuildTestSupport.cursor(h))).thenExecute(() -> {
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-12 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_check_pass", structure = YARD, skyAccess = true, maxTicks = 600)
	public void checkPassRepairs(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE);
		BlockPos wall = BuildTestSupport.cell(h, bp, pl, 0, 2, 3);
		UUID[] id = new UUID[1];
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			ItemStack[] all = BuildTestSupport.houseMaterials(1);
			for (ItemStack s : all) if (s.is(Items.OAK_PLANKS)) s.setCount(120); // one for the repair
			BuildTestSupport.stock(h, all);
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			id[0] = BuildTestSupport.onlyBuild(h);
			var a = BuildJobs.advance(h.getLevel().getServer(), id[0], 246 + 170);
			h.assertTrue(a.steps() == 416 && !a.finished(), "every main step is done: " + a);
			h.assertTrue(h.getBlockState(wall).is(Blocks.OAK_PLANKS), "the wall block stands");
			h.setBlock(wall, Blocks.AIR); // gone, and no item entity (not broken by a player)
			var b = BuildJobs.advance(h.getLevel().getServer(), id[0], Integer.MAX_VALUE);
			h.assertTrue(b.finished(), "the build finished: " + b);
			h.assertTrue(h.getBlockState(wall).is(Blocks.OAK_PLANKS), "the check pass put the wall block back");
			h.assertTrue(Stockpiles.count(h.getLevel().getServer(), Items.OAK_PLANKS) == 0, "120 planks were used (119 and the repair)");
		}).thenWaitUntil(() -> BuildTestSupport.assertHouseGreen(h, bp, pl)).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-13 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_farm_plot", structure = YARD, skyAccess = true, maxTicks = 40000)
	public void farmPlotBuilt(GameTestHelper h) {
		Blueprint bp = Blueprints.FARM_PLOT;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		var log = new BuildTestSupport.ActionLog[1];
		long[] t0 = new long[1];
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.farmMaterials(1));
			log[0] = BuildTestSupport.watchActions();
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			t0[0] = h.getTick();
		}).thenWaitUntil(() -> h.assertTrue(BuildTestSupport.builds(h).isEmpty() && log[0].places >= 121, "the Farm Plot is built: " + BuildTestSupport.cursor(h) + " " + log[0].places))
				.thenExecute(() -> {
					long ticks = h.getTick() - t0[0];
					System.out.println("[m20] farm plot finished after " + ticks + " ticks: " + log[0].places + " places, " + log[0].clears + " clears");
					h.assertTrue(ticks < Balance.DAY_TICKS * 2, "within two days at the slower pace: " + ticks);
					h.assertTrue(log[0].insideBox == 0, "every action was taken from outside the box: " + log[0].firstInside);
					checkFarm(h, bp, pl, 0);
					BlockPos gate = BuildTestSupport.cell(h, bp, pl, 5, 1, 0);
					var player = BuildTestSupport.playerWithRank(h, "farmer", Rank.RESIDENT);
					var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(gate.south(3)));
					player.setPos(at.x, at.y, at.z);
					BuildingTestSupport.rightClick(h, player, gate, Direction.SOUTH, ItemStack.EMPTY);
					h.assertTrue(h.getBlockState(gate).getValue(FenceGateBlock.OPEN), "the gate opens");
					h.getLevel().setDayTime(11100); // the day's work is over: the builder hands in its spoil
					h.getLevel().getGameRules().set(GameRules.RANDOM_TICK_SPEED, 100, h.getLevel().getServer()); // the water works through the farmland fast
				}).thenWaitUntil(() -> {
					var server = h.getLevel().getServer();
					h.assertTrue(Stockpiles.count(server, Items.BUCKET) >= 1, "the empty bucket is in Storage");
					h.assertTrue(Stockpiles.count(server, Items.DIRT) >= 1, "and the cleared grass's dirt");
				}).thenIdle(600).thenExecute(() -> {
					for (int z = 1; z <= 9; z++) for (int x = 1; x <= 9; x++) {
						BlockState s = h.getBlockState(BuildTestSupport.cell(h, bp, pl, x, 0, z));
						if (s.is(Blocks.WATER)) continue;
						h.assertTrue(s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE) == 7, "moist farmland at (" + x + ", " + z + "): " + s);
					}
					WorkerTestSupport.end(h);
					VillageTestSupport.done(h);
				});
	}

	/** The index of the first CHECK step of a build. */
	private static int firstCheck(GameTestHelper h) {
		var steps = BuildJobs.steps(h.getLevel().getServer(), BuildTestSupport.builds(h).get(0));
		for (int i = 0; i < steps.size(); i++) if (steps.get(i).kind() == dev.villageheart.core.blueprint.BuildPlan.Kind.CHECK) return i;
		throw new AssertionError("no check pass");
	}

	/**
	 * Review of M20 round 2: a farmland next to the water dries to dirt (a player trampled it) when the check pass starts. The builder replaces
	 * it in ONE action, so the water cannot flow into the gap: the plot ends with 80 farmland and 1 water.
	 */
	@GameTest(environment = "villageheart-gametest:build_farm_check", structure = YARD, skyAccess = true, maxTicks = 40000)
	public void checkPassDoesNotFloodAFarmland(GameTestHelper h) {
		Blueprint bp = Blueprints.FARM_PLOT;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		var log = new BuildTestSupport.ActionLog[1];
		BlockPos[] trampled = new BlockPos[1];
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.farmMaterials(1));
			log[0] = BuildTestSupport.watchActions();
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
		}).thenWaitUntil(() -> h.assertTrue(BuildTestSupport.cursor(h) >= firstCheck(h), "the check pass starts: " + BuildTestSupport.cursor(h)))
				.thenExecute(() -> {
					var steps = BuildJobs.steps(h.getLevel().getServer(), BuildTestSupport.builds(h).get(0));
					int cursor = BuildTestSupport.cursor(h);
					// among the farmland cells next to the water, the one whose check step is the last: it is still to come
					int best = -1;
					for (int z = 1; z <= 9; z++) for (int x = 1; x <= 9; x++) {
						BlockPos c = BuildTestSupport.cell(h, bp, pl, x, 0, z);
						if (!h.getBlockState(c).is(Blocks.FARMLAND)) continue;
						boolean nextToWater = false;
						for (Direction d : Direction.Plane.HORIZONTAL) if (h.getBlockState(c.relative(d)).is(Blocks.WATER)) nextToWater = true;
						if (!nextToWater) continue;
						Pos abs = BuildTestSupport.abs(h, c);
						for (int i = steps.size() - 1; i > cursor; i--) {
							if (steps.get(i).kind() == dev.villageheart.core.blueprint.BuildPlan.Kind.CHECK && steps.get(i).pos().equals(abs)) {
								if (i > best) { best = i; trampled[0] = c; }
								break;
							}
						}
					}
					h.assertTrue(trampled[0] != null, "a farmland next to the water is still to be checked");
					h.setBlock(trampled[0], Blocks.DIRT); // dried (or trampled)
				}).thenWaitUntil(() -> h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "the plot is finished")).thenIdle(200).thenExecute(() -> {
					int farmland = 0, water = 0;
					for (int z = 1; z <= 9; z++) for (int x = 1; x <= 9; x++) {
						BlockState st = h.getBlockState(BuildTestSupport.cell(h, bp, pl, x, 0, z));
						if (st.is(Blocks.FARMLAND)) farmland++;
						if (st.is(Blocks.WATER)) water++;
					}
					h.assertTrue(farmland == 80 && water == 1, "80 farmland and 1 water: " + farmland + "/" + water);
					h.assertTrue(h.getBlockState(trampled[0]).is(Blocks.FARMLAND), "the dried cell is farmland again");
					WorkerTestSupport.end(h);
					VillageTestSupport.done(h);
				});
	}

	/**
	 * The touched-cell exemption: the check pass finds a wall block of the House turned into dirt under the House's own wall torch. The torch is
	 * the build's own (a touched cell), so it does not stop the build; the wall comes back and so does the torch that popped.
	 */
	@GameTest(environment = "villageheart-gametest:build_own_torch", structure = YARD, skyAccess = true, maxTicks = 600)
	public void ownTorchDoesNotBlockTheCheckPass(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE);
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			ItemStack[] all = BuildTestSupport.houseMaterials(1);
			for (ItemStack s : all) {
				if (s.is(Items.TORCH)) s.setCount(2);
				if (s.is(Items.OAK_PLANKS)) s.setCount(120);
			}
			BuildTestSupport.stock(h, all);
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			UUID id = BuildTestSupport.onlyBuild(h);
			var server = h.getLevel().getServer();
			int check = firstCheck(h);
			var a = BuildJobs.advance(server, id, check);
			h.assertTrue(a.steps() == check && !a.finished(), "every main step is done: " + a);
			BlockPos torch = null;
			for (var step : BuildJobs.steps(server, BuildTestSupport.builds(h).get(0))) {
				if (step.kind() == dev.villageheart.core.blueprint.BuildPlan.Kind.PLACE && step.entry().blockId().contains("torch")) torch = BuildTestSupport.rel(h, step.pos());
			}
			h.assertTrue(torch != null && h.getBlockState(torch).getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock, "the House has a wall torch");
			BlockPos support = torch.relative(h.getBlockState(torch).getValue(net.minecraft.world.level.block.WallTorchBlock.FACING).getOpposite());
			h.assertTrue(h.getBlockState(support).is(Blocks.OAK_PLANKS) || h.getBlockState(support).is(Blocks.COBBLESTONE), "the torch hangs on a wall block: " + h.getBlockState(support));
			Block wall = h.getBlockState(support).getBlock();
			h.setBlock(support, Blocks.DIRT);
			var b = BuildJobs.advance(server, id, Integer.MAX_VALUE);
			h.assertTrue(b.finished(), "the check pass went through the build's own torch: " + b);
			h.assertTrue(h.getBlockState(support).is(wall), "the wall block is back");
			h.assertTrue(h.getBlockState(torch).getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock, "and so is the torch");
		}).thenWaitUntil(() -> BuildTestSupport.assertHouseGreen(h, bp, pl)).thenExecute(() -> VillageTestSupport.done(h));
	}

	/**
	 * Review of M20 round 3: the check pass leaves a waterlogged block of a player alone (only a fluid BLOCK in a wrong cell is repaired): a
	 * waterlogged chest with five diamonds and a waterlogged stair stand in wall cells of the House when the check pass runs.
	 */
	@GameTest(environment = "villageheart-gametest:build_waterlogged_check", structure = YARD, skyAccess = true, maxTicks = 600)
	public void checkPassLeavesWaterloggedPlayerBlocks(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE);
		BlockPos chest = BuildTestSupport.cell(h, bp, pl, 0, 2, 3), stairs = BuildTestSupport.cell(h, bp, pl, 0, 2, 2);
		BuildTestSupport.layout(h, With.STORAGE_NO_HUT).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			UUID id = BuildTestSupport.onlyBuild(h);
			var server = h.getLevel().getServer();
			var a = BuildJobs.advance(server, id, firstCheck(h));
			h.assertTrue(!a.finished(), "every main step is done: " + a);
			h.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED, true));
			h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(chest)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity, "a chest block entity");
			((net.minecraft.world.level.block.entity.ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(chest))).setItem(0, new ItemStack(Items.DIAMOND, 5));
			h.setBlock(stairs, Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED, true));
			var b = BuildJobs.advance(server, id, Integer.MAX_VALUE);
			h.assertTrue(b.finished(), "the build finished: " + b);
			h.assertTrue(h.getBlockState(chest).is(Blocks.CHEST) && h.getBlockState(chest).getValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED), "the waterlogged chest is untouched: " + h.getBlockState(chest));
			var be = h.getLevel().getBlockEntity(h.absolutePos(chest));
			h.assertTrue(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c && c.getItem(0).is(Items.DIAMOND) && c.getItem(0).getCount() == 5, "with its five diamonds");
			h.assertTrue(h.getBlockState(stairs).is(Blocks.OAK_STAIRS) && h.getBlockState(stairs).getValue(net.minecraft.world.level.block.StairBlock.WATERLOGGED), "the waterlogged stair is untouched: " + h.getBlockState(stairs));
			VillageTestSupport.done(h);
		});
	}

	/** A waterlogged sign attached after the confirm (a waterlogged block is not a fluid block): the builder stops at it and breaks nothing. */
	@GameTest(environment = "villageheart-gametest:build_waterlogged_sign", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void waterloggedAttachmentAfterConfirmBlocksTheBuilder(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		BlockPos dirt = BuildTestSupport.cell(h, bp, pl, 3, 4, 3);
		BlockPos sign = dirt.above();
		Pos signAbs = BuildTestSupport.abs(h, sign);
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			h.setBlock(dirt, Blocks.DIRT);
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			h.setBlock(sign, Blocks.OAK_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.StandingSignBlock.WATERLOGGED, true));
		}).thenWaitUntil(() -> {
			var views = BuildSystem.views(h.getLevel().getServer());
			h.assertTrue(!views.isEmpty(), "the build is listed");
			var view = views.get(0);
			h.assertTrue(view.status() == dev.villageheart.core.blueprint.BuildStatus.BLOCKED && view.line().english().equals("oak_sign in the way at " + signAbs),
					"the build stops at the sign: " + view.status() + " " + view.line().english());
		}).thenIdle(300).thenExecute(() -> {
			h.assertTrue(h.getBlockState(sign).is(Blocks.OAK_SIGN), "the sign was never broken");
			h.assertTrue(h.getBlockState(dirt).is(Blocks.DIRT), "and neither was the dirt under it");
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-14 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_entity", structure = YARD, skyAccess = true, maxTicks = 20000)
	public void entityInTheWay(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.CLOCKWISE_180);
		BlockPos cell = BuildTestSupport.cell(h, bp, pl, 0, 1, 3);
		Pos cellAbs = pl.world(bp, 0, 1, 3);
		var pen = new net.minecraft.world.entity.npc.villager.Villager[1];
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(1));
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			pen[0] = WorkerTestSupport.spawnVillager(h, cell);
			pen[0].setNoAi(true);
			pen[0].setNoGravity(true);
			pen[0].snapTo(h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(cell)).x, h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(cell)).y,
					h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(cell)).z, 0, 0);
		}).thenWaitUntil(() -> {
			var view = BuildSystem.views(h.getLevel().getServer()).get(0);
			h.assertTrue(view.status() == dev.villageheart.core.blueprint.BuildStatus.IN_THE_WAY
					&& view.line().english().equals("Something is in the way at " + cellAbs), "the build waits for the villager, naming the cell " + cellAbs + ": "
					+ view.status() + " " + view.line().english() + " claimed=" + view.claimed() + " pause=" + BuildRuntime.get(h.getLevel().getServer()).pause(view.job().id())
					+ " now=" + h.getLevel().getServer().overworld().getGameTime() + " day=" + h.getLevel().getDayTime());
		}).thenIdle(100).thenExecute(() -> {
			h.assertTrue(h.getBlockState(cell).isAir(), "no block was placed into the entity");
			var out = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(30, 0, 30)));
			pen[0].teleportTo(out.x, out.y, out.z);
		}).thenWaitUntil(() -> h.assertTrue(h.getBlockState(cell).is(Blocks.OAK_PLANKS), "the wall block is placed once the villager is gone"))
				.thenExecute(() -> {
					WorkerTestSupport.end(h);
					VillageTestSupport.done(h);
				});
	}

	// ---- T-L2-16 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_kept_materials", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void keptMaterialsNoTripLoop(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		var builder = new net.minecraft.world.entity.npc.villager.Villager[1];
		int[] trips = new int[1];
		long[] before = new long[1];
		boolean[] watch = new boolean[1];
		int[] cursor0 = new int[1];
		WorkerTestSupport.begin(h);
		h.onEachTick(() -> {
			if (watch[0] && builder[0] != null && dev.villageheart.worker.Workers.tripRunning(builder[0])) trips[0]++;
		});
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			builder[0] = BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			BuildTestSupport.confirmSite(h, bp, Rotation4.CLOCKWISE_180);
			// seven slots of kept materials and no spoil: the build's carried map names them all
			Item[] kept = {Items.COBBLESTONE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS, Items.DARK_OAK_PLANKS};
			java.util.Map<String, Integer> carried = new java.util.HashMap<>();
			for (int i = 0; i < kept.length; i++) {
				builder[0].getInventory().setItem(i, new ItemStack(kept[i], 64));
				carried.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kept[i]).toString(), 64);
			}
			var job = BuildTestSupport.builds(h).get(0);
			VillageClaims.data(h.getLevel().getServer()).update(v -> v.withBuild(job.withCarried(carried).withCarrier(builder[0].getUUID())));
			before[0] = Stockpiles.snapshot(h.getLevel().getServer()).totals().stream().mapToLong(t -> t.count()).sum();
			cursor0[0] = BuildTestSupport.cursor(h);
			watch[0] = true;
		}).thenIdle(400).thenExecute(() -> {
			h.assertTrue(trips[0] <= 1, "at most one deposit trip in 400 ticks: " + trips[0]);
			long after = Stockpiles.snapshot(h.getLevel().getServer()).totals().stream().mapToLong(t -> t.count()).sum();
			h.assertTrue(after == before[0], "nothing was handed in: " + before[0] + " -> " + after);
			h.assertTrue(BuildTestSupport.cursor(h) > cursor0[0] + 5, "the builder places with what it carries: " + cursor0[0] + " -> " + BuildTestSupport.cursor(h));
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-15 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:build_two_builders", structure = YARD, skyAccess = true, maxTicks = 80000)
	public void twoBuildersTwoBuilds(GameTestHelper h) {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		BlockPos groundA = new BlockPos(10, -1, 22), groundB = new BlockPos(22, -1, 22);
		Placement plA = BuildTestSupport.placement(h, groundA, Rotation4.NONE), plB = BuildTestSupport.placement(h, groundB, Rotation4.CLOCKWISE_90);
		Map<String, Long> before = new HashMap<>();
		UUID[] ids = new UUID[2];
		boolean[] distinct = new boolean[1];
		boolean[] watch = new boolean[1];
		WorkerTestSupport.begin(h);
		h.onEachTick(() -> {
			if (!watch[0]) return;
			var claims = BuildRuntime.get(h.getLevel().getServer()).claims();
			if (claims.size() == 2 && !claims.get(ids[0]).equals(claims.get(ids[1]))) distinct[0] = true;
			if (!distinct[0] && claims.size() == 1 && claims.containsKey(ids[1]) && !claims.containsKey(ids[0]) && BuildTestSupport.builds(h).size() == 2) {
				throw h.assertionException("the younger build was claimed first");
			}
		});
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			h.setBlock(BuildingTestSupport.inside(BuildTestSupport.HUT, 4, 0, 0), JobBlocks.BLOCKS.get(dev.villageheart.core.building.JobBlockType.BUILDERS_TABLE));
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, BuildTestSupport.hutMarker(), MarkerStatus.VALID);
			h.assertTrue(BuildingTestSupport.verdict(h, BuildTestSupport.hutMarker()).orElseThrow().slots() == 2, "the Hut has two worker slots");
		}).thenExecute(() -> {
			BuildTestSupport.stock(h, BuildTestSupport.houseMaterials(2));
			before.putAll(Stockpiles.totalsById(h.getLevel().getServer()));
			BuildTestSupport.startBuild(h, bp, groundA, Rotation4.NONE);
			BuildTestSupport.startBuild(h, bp, groundB, Rotation4.CLOCKWISE_90);
			ids[0] = BuildTestSupport.builds(h).get(0).id();
			ids[1] = BuildTestSupport.builds(h).get(1).id();
			BuildTestSupport.hireBuilder(h);
			BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			watch[0] = true;
		}).thenWaitUntil(() -> h.assertTrue(distinct[0], "each builder claims its own build")).thenWaitUntil(() -> {
			BuildTestSupport.assertHouseGreen(h, bp, plA);
			BuildTestSupport.assertHouseGreen(h, bp, plB);
		}).thenExecute(() -> {
			assertExactDeduction(h, before, Map.of(), "both Houses used exactly their materials");
			watch[0] = false;
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}
}
