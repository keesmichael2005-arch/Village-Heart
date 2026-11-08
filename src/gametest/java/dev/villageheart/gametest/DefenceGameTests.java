package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.YARD;
import static dev.villageheart.gametest.DefenceTestSupport.env;

import dev.villageheart.build.BlueprintItem;
import dev.villageheart.build.BlueprintSite;
import dev.villageheart.build.BuildJobs;
import dev.villageheart.build.ModComponents;
import dev.villageheart.build.SiteValidation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.blueprint.SiteRules;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import dev.villageheart.gametest.BuildTestSupport.With;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Layer 2: the defence blueprints (M21): wall, gate, lamp post, watchtower. Rules as for the builder tests (docs/TESTING.md): own
 * environment per test ({@code defence_<name>}), the {@code yard} structure with sky access, a layout first, {@code VillageTestSupport.done} last.
 */
public class DefenceGameTests {
	private static final Rotation4[] ROTATIONS = Rotation4.values();
	private static final BlockPos[] GRID = BuildTestSupport.GRID;

	private static void tier(GameTestHelper h, Tier t) { BuildingTestSupport.setTier(h, t); }

	private static BlockPos rel(int x, int y, int z) { return new BlockPos(x, y, z); }

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	/** The lamp post and the watchtower are placed in all four rotations and their lantern and ladders are still there 20 ticks later. */
	@GameTest(environment = "villageheart-gametest:defence_attach", structure = YARD, skyAccess = true, maxTicks = 600)
	public void defenceAttachmentsSurvive(GameTestHelper h) {
		BlockPos[] lamps = {rel(13, -1, 12), rel(26, -1, 12), rel(13, -1, 25), rel(26, -1, 25)};
		List<Object[]> built = new ArrayList<>();
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			tier(h, Tier.TOWN);
			BuildTestSupport.stock(h, DefenceTestSupport.concat(DefenceTestSupport.towerMaterials(4), DefenceTestSupport.lampMaterials(4)));
			for (int i = 0; i < 4; i++) {
				BuildTestSupport.startBuild(h, Blueprints.LAMP_POST, lamps[i], ROTATIONS[i]);
				BuildTestSupport.startBuild(h, Blueprints.WATCHTOWER, GRID[i], ROTATIONS[i]);
				built.add(new Object[] {Blueprints.LAMP_POST, BuildTestSupport.placement(h, lamps[i], ROTATIONS[i])});
				built.add(new Object[] {Blueprints.WATCHTOWER, BuildTestSupport.placement(h, GRID[i], ROTATIONS[i])});
			}
			h.assertTrue(BuildTestSupport.builds(h).size() == 8, "eight builds started: " + BuildTestSupport.builds(h).size());
			DefenceTestSupport.advanceAll(h);
		}).thenIdle(20).thenExecute(() -> {
			for (Object[] b : built) {
				Blueprint bp = (Blueprint) b[0];
				Placement p = (Placement) b[1];
				DefenceTestSupport.assertBuilt(h, bp, p, bp.id() + " " + p.rotation());
				if (bp == Blueprints.LAMP_POST) {
					BlockState lantern = h.getBlockState(BuildTestSupport.cell(h, bp, p, 0, 2, 0));
					h.assertTrue(lantern.is(Blocks.LANTERN) && !lantern.getValue(LanternBlock.HANGING), "the lantern stands on the post in " + p.rotation());
				} else {
					Direction facing = Direction.valueOf(p.rotation().apply(Dir.NORTH).name());
					for (int y = 0; y <= 5; y++) {
						BlockState ladder = h.getBlockState(BuildTestSupport.cell(h, bp, p, 2, y, 2));
						h.assertTrue(ladder.is(Blocks.LADDER) && ladder.getValue(net.minecraft.world.level.block.LadderBlock.FACING) == facing,
								"ladder " + y + " faces " + facing + " in " + p.rotation() + ": " + ladder);
					}
					// the rail connects to its neighbours
					for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
						PaletteEntry e = bp.entry(x, 6, z);
						if (!e.blockId().equals("minecraft:oak_fence")) continue;
						BlockState fence = h.getBlockState(BuildTestSupport.cell(h, bp, p, x, 6, z));
						for (Direction d : Direction.Plane.HORIZONTAL) {
							Pos at = BuildTestSupport.abs(h, BuildTestSupport.cell(h, bp, p, x, 6, z)).offset(Dir.valueOf(d.name()));
							PaletteEntry n = p.entryAt(bp, at);
							boolean neighbourIsFence = n != null && n.blockId().equals("minecraft:oak_fence");
							h.assertTrue(fence.getValue(net.minecraft.world.level.block.CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(d)) == neighbourIsFence,
									"rail fence (" + x + "," + z + ") side " + d + " connects=" + neighbourIsFence + " in " + p.rotation());
						}
					}
				}
			}
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	/** Walls and gates in the four directions, built instantly: every cell equals the blueprint, the ground is unchanged, gates face across the wall. */
	@GameTest(environment = "villageheart-gametest:defence_instant", structure = YARD, skyAccess = true, maxTicks = 600)
	public void instantDefencesAllRotations(GameTestHelper h) {
		// {blueprint, from, to}
		Object[][] lines = {
				{"wall_segment", rel(2, -1, 12), rel(9, -1, 12)}, {"wall_segment", rel(21, -1, 12), rel(14, -1, 12)},
				{"wall_segment", rel(3, -1, 22), rel(3, -1, 29)}, {"wall_segment", rel(8, -1, 29), rel(8, -1, 22)},
				{"gate", rel(2, -1, 16), rel(8, -1, 16)}, {"gate", rel(21, -1, 16), rel(15, -1, 16)},
				{"gate", rel(13, -1, 22), rel(13, -1, 28)}, {"gate", rel(18, -1, 28), rel(18, -1, 22)}};
		Rotation4[] expected = {Rotation4.NONE, Rotation4.CLOCKWISE_180, Rotation4.CLOCKWISE_90, Rotation4.COUNTERCLOCKWISE_90,
				Rotation4.NONE, Rotation4.CLOCKWISE_180, Rotation4.CLOCKWISE_90, Rotation4.COUNTERCLOCKWISE_90};
		List<Blueprint> bps = new ArrayList<>();
		List<Placement> places = new ArrayList<>();
		List<Integer> hashes = new ArrayList<>();
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 4 * 24 + 4 * 15), new ItemStack(Items.OAK_FENCE_GATE, 12));
			for (Object[] l : lines) DefenceTestSupport.startLine(h, (String) l[0], (BlockPos) l[1], (BlockPos) l[2]);
			List<BuildJob> jobs = BuildTestSupport.builds(h);
			h.assertTrue(jobs.size() == 8, "eight lines started: " + jobs.size());
			for (int i = 0; i < 8; i++) {
				BuildJob j = jobs.get(i);
				h.assertTrue(j.blueprintId().equals(lines[i][0]) && j.params().length() == (i < 4 ? 8 : 7), "line " + i + ": " + j.blueprintId() + " " + j.params());
				h.assertTrue(j.rotation() == expected[i], "line " + i + " rotation " + j.rotation());
				bps.add(BuildJobs.blueprint(j));
				places.add(j.placement());
				hashes.add(DefenceTestSupport.groundHash(h, bps.get(i), places.get(i)));
			}
			DefenceTestSupport.advanceAll(h);
		}).thenExecute(() -> {
			for (int i = 0; i < 8; i++) {
				Blueprint bp = bps.get(i);
				Placement p = places.get(i);
				DefenceTestSupport.assertBuilt(h, bp, p, "line " + i);
				h.assertTrue(DefenceTestSupport.groundHash(h, bp, p) == hashes.get(i), "the ground under line " + i + " is unchanged");
				if (i >= 4) {
					Dir along = p.rotation().apply(Dir.EAST);
					int gates = 0;
					for (int x = 0; x < bp.width(); x++) {
						BlockState s = h.getBlockState(BuildTestSupport.cell(h, bp, p, x, 0, 0));
						if (!(s.getBlock() instanceof FenceGateBlock)) continue;
						gates++;
						Direction f = s.getValue(FenceGateBlock.FACING);
						h.assertTrue(f.getAxis() != Direction.valueOf(along.name()).getAxis(), "gate " + i + " faces across the wall: " + f + " along " + along);
						h.assertTrue(!s.getValue(FenceGateBlock.OPEN), "the gate is closed");
					}
					h.assertTrue(gates == 3, "three gates in line " + i);
				}
			}
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-3 (roadmap) ------------------------------------------------------------------------

	/** A 20-long wall, started with the real item (start, end, confirm) and built by a builder villager from exactly 60 cobblestone. */
	@GameTest(environment = "villageheart-gametest:defence_wall20", structure = YARD, skyAccess = true, maxTicks = 12000)
	public void wallTwentyBuiltComplete(GameTestHelper h) {
		Blueprint unit = Blueprints.WALL_SEGMENT;
		Blueprint wall = Blueprints.resolve("wall_segment", new BlueprintParams(20)).orElseThrow();
		var log = new BuildTestSupport.ActionLog[1];
		var builder = new net.minecraft.world.entity.npc.villager.Villager[1];
		var steward = new BuildingTestSupport.RecordingPlayer[1];
		ItemStack stack = BlueprintItem.of(unit);
		Map<String, Long> before = new HashMap<>();
		long[] t0 = new long[1];
		BlockPos start = rel(6, -1, 20), end = rel(25, -1, 20);
		Placement pl = BuildTestSupport.placement(h, start, Rotation4.NONE);
		WorkerTestSupport.begin(h);
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 60));
			before.putAll(Stockpiles.totalsById(h.getLevel().getServer()));
			log[0] = BuildTestSupport.watchActions();
			builder[0] = BuildTestSupport.hireBuilder(h);
			h.getLevel().setDayTime(1000);
			steward[0] = BuildTestSupport.playerWithRank(h, "steward", Rank.STEWARD);
			BuildingTestSupport.rightClick(h, steward[0], start, Direction.UP, stack);
			BlueprintSite s1 = stack.get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(s1 != null && s1.ok() && s1.length() == 0 && s1.ground().equals(h.absolutePos(start)), "click 1 stores the start: " + s1);
			BuildingTestSupport.rightClick(h, steward[0], end, Direction.UP, stack);
			BlueprintSite s2 = stack.get(ModComponents.BLUEPRINT_SITE);
			h.assertTrue(s2 != null && s2.ok() && s2.length() == 20 && s2.rotationEnum() == Rotation4.NONE, "click 2 shows the 20-long line: " + s2);
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "nothing is built by showing the line");
			BuildingTestSupport.rightClick(h, steward[0], start, Direction.UP, stack);
			List<BuildJob> builds = BuildTestSupport.builds(h);
			h.assertTrue(builds.size() == 1 && builds.get(0).params().length() == 20, "click 3 confirms a build of length 20: " + builds);
			h.assertTrue(stack.isEmpty() && stack.get(ModComponents.BLUEPRINT_SITE) == null, "the blueprint is used up");
			t0[0] = h.getTick();
		}).thenWaitUntil(() -> {
			boolean all = true;
			for (int x = 0; x < 20 && all; x++) for (int y = 0; y < 3; y++) {
				if (!h.getBlockState(BuildTestSupport.cell(h, wall, pl, x, y, 0)).is(Blocks.COBBLESTONE)) { all = false; break; }
			}
			h.assertTrue(all && BuildTestSupport.builds(h).isEmpty(), "the wall is built: cursor " + BuildTestSupport.cursor(h) + " places " + log[0].places);
		}).thenExecute(() -> {
			long ticks = h.getTick() - t0[0];
			System.out.println("[m21] wall of 20 finished after " + ticks + " ticks: " + log[0].places + " places, " + log[0].clears + " clears");
			h.assertTrue(ticks < 6000, "within 6000 ticks of the confirm: " + ticks);
			DefenceTestSupport.assertBuilt(h, wall, pl, "wall");
			Map<String, Long> after = Stockpiles.totalsById(h.getLevel().getServer());
			Map<String, Long> expected = new HashMap<>(before);
			expected.remove("minecraft:cobblestone");
			h.assertTrue(after.equals(expected), "exactly the 60 cobblestone are gone: " + after + " from " + before);
			h.assertTrue(builder[0].getInventory().countItem(Items.COBBLESTONE) == 0, "the builder carries no cobblestone");
			h.assertTrue(h.getEntities(EntityType.ITEM, rel(16, 3, 16), 40).isEmpty(), "no item entity in the yard");
			h.assertTrue(log[0].places == 60, "60 placements: " + log[0].places);
			h.assertTrue(log[0].insideBox == 0, "every action was taken from outside the box: " + log[0].firstInside);
			dev.villageheart.worker.build.BuilderJob.listener = null;
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-4 (roadmap) ------------------------------------------------------------------------

	private static AABB playerBox(GameTestHelper h, BlockPos cellRel) {
		BlockPos a = h.absolutePos(cellRel);
		return new AABB(a.getX() + 0.2, a.getY(), a.getZ() + 0.2, a.getX() + 0.8, a.getY() + 1.8, a.getZ() + 0.8);
	}

	/** The index of the first CHECK step of the only build. */
	private static int firstCheck(GameTestHelper h) {
		var steps = BuildJobs.steps(h.getLevel().getServer(), BuildTestSupport.builds(h).get(0));
		for (int i = 0; i < steps.size(); i++) if (steps.get(i).kind() == BuildPlan.Kind.CHECK) return i;
		throw new AssertionError("no check pass");
	}

	@GameTest(environment = "villageheart-gametest:defence_gate", structure = YARD, skyAccess = true, maxTicks = 600)
	public void gateOpens(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 30), new ItemStack(Items.OAK_FENCE_GATE, 6));
			DefenceTestSupport.startLine(h, "gate", rel(4, -1, 15), rel(10, -1, 15));
			DefenceTestSupport.advanceAll(h);
			for (int x = 6; x <= 8; x++) {
				BlockState g = h.getBlockState(rel(x, 0, 15));
				h.assertTrue(g.getBlock() instanceof FenceGateBlock && !g.getValue(FenceGateBlock.OPEN), "closed fence gate at x " + x + ": " + g);
				h.assertTrue(h.getBlockState(rel(x, 1, 15)).isAir(), "air above the gate at x " + x);
				h.assertTrue(h.getBlockState(rel(x, 2, 15)).is(Blocks.COBBLESTONE), "the lintel at x " + x);
			}
			AABB box = playerBox(h, rel(7, 0, 15));
			h.assertTrue(!h.getLevel().noCollision(box), "a closed gate blocks a player");
			var player = BuildTestSupport.playerWithRank(h, "walker", Rank.RESIDENT);
			var at = h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(rel(7, 0, 18)));
			player.setPos(at.x, at.y, at.z);
			for (int x = 6; x <= 8; x++) {
				BuildingTestSupport.rightClick(h, player, rel(x, 0, 15), Direction.SOUTH, ItemStack.EMPTY);
				h.assertTrue(h.getBlockState(rel(x, 0, 15)).getValue(FenceGateBlock.OPEN), "gate " + x + " opens");
			}
			h.assertTrue(h.getLevel().noCollision(box), "an open gate lets a 2-high player through");
			BuildingTestSupport.rightClick(h, player, rel(7, 0, 15), Direction.SOUTH, ItemStack.EMPTY);
			h.assertTrue(!h.getBlockState(rel(7, 0, 15)).getValue(FenceGateBlock.OPEN) && !h.getLevel().noCollision(box), "and closes again");
			// the check pass of a second gate does not "repair" a gate a player opened
			DefenceTestSupport.startLine(h, "gate", rel(4, -1, 20), rel(10, -1, 20));
			int check = firstCheck(h);
			UUID id = BuildTestSupport.onlyBuild(h);
			var a = BuildJobs.advance(h.getLevel().getServer(), id, check);
			h.assertTrue(a.steps() == check && !a.finished(), "every clear and place step is done: " + a + " check at " + check);
			BuildingTestSupport.rightClick(h, player, rel(7, 0, 20), Direction.SOUTH, ItemStack.EMPTY);
			h.assertTrue(h.getBlockState(rel(7, 0, 20)).getValue(FenceGateBlock.OPEN), "the middle gate of the second line opens");
			var b = BuildJobs.advance(h.getLevel().getServer(), id, Integer.MAX_VALUE);
			h.assertTrue(b.finished(), "the second gate is finished: " + b);
			BlockState kept = h.getBlockState(rel(7, 0, 20));
			h.assertTrue(kept.getBlock() instanceof FenceGateBlock && kept.getValue(FenceGateBlock.OPEN), "the check pass left the opened gate alone: " + kept);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	private static BlueprintSite site(GameTestHelper h, ItemStack s) {
		BlueprintSite site = s.get(ModComponents.BLUEPRINT_SITE);
		h.assertTrue(site != null, "the stack shows a site");
		return site;
	}

	@GameTest(environment = "villageheart-gametest:defence_lines", structure = YARD, skyAccess = true, maxTicks = 600)
	public void lineFlowRefusals(GameTestHelper h) {
		BuildTestSupport.layout(h, With.STANDARD).thenExecute(() -> {
			var steward = BuildTestSupport.playerWithRank(h, "steward", Rank.STEWARD);
			var resident = BuildTestSupport.playerWithRank(h, "resident", Rank.RESIDENT);
			// the world: a dip under column 7 of a line, glass in another line, short grass at the start of a third
			h.setBlock(rel(7, -1, 24), Blocks.AIR);
			h.setBlock(rel(6, 1, 26), Blocks.GLASS);
			h.setBlock(rel(2, 0, 22), Blocks.SHORT_GRASS);
			int hash = BuildTestSupport.worldHash(h);
			// diagonal: refused, the start stays
			ItemStack wall = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 20), Direction.UP, wall);
			BuildingTestSupport.rightClick(h, steward, rel(10, -1, 22), Direction.UP, wall);
			BlueprintSite s = site(h, wall);
			h.assertTrue(!s.ok() && s.length() == 0 && s.reason().key().equals("build.villageheart.site.not_straight") && s.ground().equals(h.absolutePos(rel(2, -1, 20))),
					"a diagonal is refused and the start is kept: " + s);
			h.assertTrue(s.reason().english().equals("A wall must run straight along x or z (2 off the line)"), "with the number: " + s.reason().english());
			// the next click is another end
			BuildingTestSupport.rightClick(h, steward, rel(12, -1, 20), Direction.UP, wall);
			s = site(h, wall);
			h.assertTrue(s.ok() && s.length() == 11, "another end gives a line: " + s);
			// too long
			ItemStack longWall = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(0, -1, 18), Direction.UP, longWall);
			BuildingTestSupport.rightClick(h, steward, rel(32, -1, 18), Direction.UP, longWall);
			s = site(h, longWall);
			h.assertTrue(!s.ok() && s.reason().key().equals("build.villageheart.site.too_long") && s.reason().english().equals("Too long: 33 blocks (max 32)"), "33 is too long: " + s);
			// a gate of 4
			ItemStack gate = BlueprintItem.of(Blueprints.GATE);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 20), Direction.UP, gate);
			BuildingTestSupport.rightClick(h, steward, rel(5, -1, 20), Direction.UP, gate);
			s = site(h, gate);
			h.assertTrue(!s.ok() && s.reason().key().equals("build.villageheart.site.too_short"), "a 4-long gate is too short: " + s);
			// the dip
			ItemStack dip = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 24), Direction.UP, dip);
			BuildingTestSupport.rightClick(h, steward, rel(12, -1, 24), Direction.UP, dip);
			s = site(h, dip);
			BlockPos dipCell = h.absolutePos(rel(7, 0, 24));
			h.assertTrue(!s.ok() && s.reason().key().equals("build.villageheart.site.footing")
					&& s.reason().english().equals("No ground under (" + dipCell.getX() + ", " + dipCell.getY() + ", " + dipCell.getZ() + "): defences need level ground"),
					"the dip names its column: " + s.reason().english());
			// the glass
			ItemStack glass = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 26), Direction.UP, glass);
			BuildingTestSupport.rightClick(h, steward, rel(12, -1, 26), Direction.UP, glass);
			s = site(h, glass);
			h.assertTrue(!s.ok() && s.reason().english().startsWith("glass in the way"), "glass is in the way: " + s.reason().english());
			// through the Storage
			ItemStack through = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 6), Direction.UP, through);
			BuildingTestSupport.rightClick(h, steward, rel(12, -1, 6), Direction.UP, through);
			s = site(h, through);
			h.assertTrue(!s.ok() && s.reason().key().equals("build.villageheart.site.overlap"), "a line through a building overlaps it: " + s.reason().english());
			// a Resident
			ItemStack res = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, resident, rel(2, -1, 20), Direction.UP, res);
			s = site(h, res);
			h.assertTrue(!s.ok() && s.reason().english().equals("Only a Steward or the Owner can start a blueprint"), "a Resident gets the rank text: " + s.reason().english());
			BuildingTestSupport.rightClick(h, resident, rel(12, -1, 20), Direction.UP, res);
			h.assertTrue(!site(h, res).ok() && BuildTestSupport.builds(h).isEmpty(), "and cannot build");
			// sneak: start over
			ItemStack sneak = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 20), Direction.UP, sneak);
			steward.setShiftKeyDown(true);
			BuildingTestSupport.rightClick(h, steward, rel(2, -1, 20), Direction.UP, sneak);
			steward.setShiftKeyDown(false);
			h.assertTrue(sneak.get(ModComponents.BLUEPRINT_SITE) == null, "a sneak-click clears the start");
			h.assertTrue(BuildTestSupport.lastOverlayKey(h, steward).equals("build.villageheart.msg.line_cleared"), "and says so");
			// nothing changed anywhere
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "no refusal started a build");
			h.assertTrue(BuildTestSupport.worldHash(h) == hash, "no refusal changed the world");
			// a start on short grass uses the block below; a click on the shown line confirms (it does not shorten it)
			ItemStack ok = BlueprintItem.of(Blueprints.WALL_SEGMENT);
			BuildingTestSupport.rightClick(h, steward, rel(2, 0, 22), Direction.UP, ok);
			h.assertTrue(site(h, ok).ground().equals(h.absolutePos(rel(2, -1, 22))), "the start on short grass is the block below: " + site(h, ok).ground());
			BuildingTestSupport.rightClick(h, steward, rel(11, -1, 22), Direction.UP, ok);
			s = site(h, ok);
			h.assertTrue(s.ok() && s.length() == 10, "a 10-long line: " + s);
			BuildingTestSupport.rightClick(h, steward, rel(6, -1, 22), Direction.UP, ok);
			List<BuildJob> builds = BuildTestSupport.builds(h);
			h.assertTrue(builds.size() == 1 && builds.get(0).params().length() == 10, "a click on the shown line confirms: " + builds);
			h.assertTrue(ok.isEmpty(), "and uses the blueprint");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:defence_tower", structure = YARD, skyAccess = true, maxTicks = 900)
	public void watchtowerTownAndPatrolPoints(GameTestHelper h) {
		BlockPos[] lantern = new BlockPos[1];
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, DefenceTestSupport.concat(DefenceTestSupport.towerMaterials(1), DefenceTestSupport.lampMaterials(1)));
			var steward = BuildTestSupport.playerWithRank(h, "steward", Rank.STEWARD);
			ItemStack stack = BlueprintItem.of(Blueprints.WATCHTOWER);
			BuildingTestSupport.rightClick(h, steward, BuildTestSupport.GROUND, Direction.UP, stack);
			BlueprintSite s = site(h, stack);
			h.assertTrue(!s.ok() && s.reason().english().equals("Requires Town tier"), "a Village cannot build a watchtower: " + s.reason().english());
			BuildingTestSupport.rightClick(h, steward, BuildTestSupport.GROUND, Direction.UP, stack);
			h.assertTrue(BuildTestSupport.builds(h).isEmpty(), "confirming below Town builds nothing");
			tier(h, Tier.TOWN);
			// a click on the shown box confirms with a fresh check (M20): at Town it passes
			BuildingTestSupport.rightClick(h, steward, BuildTestSupport.GROUND, Direction.UP, stack);
			h.assertTrue(BuildTestSupport.builds(h).size() == 1, "at Town the click on the shown site confirms");
			DefenceTestSupport.advanceAll(h);
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			h.assertTrue(village.defences().size() == 1 && village.defences().get(0).blueprintId().equals("watchtower"), "the tower is recorded: " + village.defences());
			BuildTestSupport.startBuild(h, Blueprints.LAMP_POST, rel(24, -1, 14), Rotation4.CLOCKWISE_90);
			DefenceTestSupport.advanceAll(h);
			village = VillageClaims.current(server).orElseThrow();
			h.assertTrue(village.defences().size() == 2, "the lamp is recorded too: " + village.defences());
			List<Pos> expected = dev.villageheart.core.defence.Defences.patrolPoints(village.defences(), p -> true);
			List<BlockPos> got = dev.villageheart.defence.Defences.patrolPoints(server);
			h.assertTrue(got.size() == 2, "two patrol points: " + got);
			for (int i = 0; i < 2; i++) {
				h.assertTrue(got.get(i).equals(new BlockPos(expected.get(i).x(), expected.get(i).y(), expected.get(i).z())), "patrol point " + i + " " + got.get(i) + " vs " + expected.get(i));
			}
			DefenceSite lamp = village.defences().get(1);
			Pos key = dev.villageheart.core.defence.Defences.keys(lamp).get(0).at();
			lantern[0] = BuildTestSupport.rel(h, key);
			h.assertTrue(h.getBlockState(lantern[0]).is(Blocks.LANTERN), "the lantern stands");
			h.setBlock(lantern[0], Blocks.AIR);
		}).thenWaitUntil(() -> {
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			h.assertTrue(village.defences().size() == 1, "the lamp is removed within one refresh: " + village.defences());
		}).thenExecute(() -> {
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			h.assertTrue(village.defences().get(0).blueprintId().equals("watchtower"), "the tower stays");
			h.assertTrue(dev.villageheart.defence.Defences.patrolPoints(server).size() == 1, "one patrol point is left");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	/** The check pass puts a ladder back after its wall block went (the ladder popped): the wall first, then the ladder. */
	@GameTest(environment = "villageheart-gametest:defence_check", structure = YARD, skyAccess = true, maxTicks = 600)
	public void towerCheckPassRepairsLadder(GameTestHelper h) {
		Blueprint bp = Blueprints.WATCHTOWER;
		Placement pl = BuildTestSupport.placement(h, BuildTestSupport.GROUND, Rotation4.NONE);
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			tier(h, Tier.TOWN);
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 39), new ItemStack(Items.OAK_PLANKS, 24), new ItemStack(Items.OAK_FENCE, 16),
					new ItemStack(Items.LADDER, 7));
			BuildTestSupport.startBuild(h, bp, BuildTestSupport.GROUND, Rotation4.NONE);
			UUID id = BuildTestSupport.onlyBuild(h);
			int check = firstCheck(h);
			var a = BuildJobs.advance(h.getLevel().getServer(), id, check);
			h.assertTrue(a.steps() == check && !a.finished(), "every clear and place step is done: " + a);
			BlockPos ladder = BuildTestSupport.cell(h, bp, pl, 2, 2, 2), support = BuildTestSupport.cell(h, bp, pl, 2, 2, 3);
			h.assertTrue(h.getBlockState(ladder).is(Blocks.LADDER) && h.getBlockState(support).is(Blocks.COBBLESTONE), "the ladder stands on its wall");
			h.setBlock(support, Blocks.AIR);
			h.assertTrue(!h.getBlockState(ladder).is(Blocks.LADDER), "the ladder popped with its wall");
			var b = BuildJobs.advance(h.getLevel().getServer(), id, Integer.MAX_VALUE);
			h.assertTrue(b.finished() && b.stoppedBecause().isEmpty(), "the check pass finished without being blocked: " + b);
			h.assertTrue(h.getBlockState(support).is(Blocks.COBBLESTONE) && h.getBlockState(ladder).is(Blocks.LADDER), "both blocks are back");
			DefenceTestSupport.assertBuilt(h, bp, pl, "tower");
			var totals = Stockpiles.totalsById(h.getLevel().getServer());
			h.assertTrue(totals.isEmpty(), "the list plus one cobblestone and one ladder was used: " + totals);
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	/** Walls queued end to end and round a corner; a House next to a pending wall is refused; the three walls join without a gap. */
	@GameTest(environment = "villageheart-gametest:defence_queue", structure = YARD, skyAccess = true, maxTicks = 600)
	public void wallsQueuedEndToEnd(GameTestHelper h) {
		BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 90));
			DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 15), rel(11, -1, 15)); // A
			DefenceTestSupport.startLine(h, "wall_segment", rel(12, -1, 15), rel(21, -1, 15)); // B, touching A's end
			DefenceTestSupport.startLine(h, "wall_segment", rel(11, -1, 16), rel(11, -1, 25)); // C, beside A's end going south
			h.assertTrue(BuildTestSupport.builds(h).size() == 3, "all three walls were accepted: " + BuildTestSupport.builds(h).size());
			var server = h.getLevel().getServer();
			var village = VillageClaims.current(server).orElseThrow();
			BlockPos g = h.absolutePos(rel(16, -1, 10));
			SiteRules.SiteProblem near = SiteValidation.check(h.getLevel(), village, Blueprints.SMALL_HOUSE,
					new Placement(new Pos(g.getX(), g.getY(), g.getZ()), Rotation4.NONE), Rank.OWNER);
			h.assertTrue(near != null && near.code() == SiteRules.Code.BUILD_OVERLAP, "a House one block from a pending wall is refused: " + (near == null ? "ok" : near.english()));
			DefenceTestSupport.advanceAll(h);
			for (int x = 2; x <= 21; x++) for (int y = 0; y <= 2; y++) {
				h.assertTrue(h.getBlockState(rel(x, y, 15)).is(Blocks.COBBLESTONE), "A and B are continuous at " + x + "," + y);
			}
			for (int z = 16; z <= 25; z++) for (int y = 0; y <= 2; y++) {
				h.assertTrue(h.getBlockState(rel(11, y, z)).is(Blocks.COBBLESTONE), "C is continuous at z " + z + "," + y);
			}
			h.assertTrue(Stockpiles.totalsById(h.getLevel().getServer()).isEmpty(), "all 90 cobblestone were used");
			VillageTestSupport.done(h);
		});
	}
}
