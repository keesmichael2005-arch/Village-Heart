package dev.villageheart.gametest;

import static dev.villageheart.gametest.FarmTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.farm.FieldCell;
import dev.villageheart.gametest.FarmTestSupport.Watcher;
import dev.villageheart.worker.farm.FarmRuntime;
import dev.villageheart.worker.farm.LevelFieldView;
import dev.villageheart.worker.job.WorkStats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: the farmer (M10). Rules (docs/TESTING.md, "Farmer GameTests"): own {@code farm_<name>} environment with
 * {@code random_tick_speed 0} (every age comes from the test, the replant or a bone meal), the {@code yard} structure with
 * {@code skyAccess}, {@code BuildingTestSupport.begin} first and {@code VillageTestSupport.done} last, fields built after the buildings
 * validate, measured bounds. Every roadmap test logs its actual tick counts ({@code [m10] ...}).
 */
public class FarmerGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static void log(String s) { System.out.println("[m10] " + s); }

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_view", structure = YARD, skyAccess = true, maxTicks = 300)
	public void fieldViewClassifies(GameTestHelper h) {
		begin(h);
		assertRandomTicksOff(h);
		LumberTestSupport.sweepStrayTrees(h, 8);
		LumberTestSupport.grass(h, 0, 0, 31, 31);
		plantAt(h, 5, 5, CropKind.WHEAT, 7);
		plantAt(h, 7, 5, CropKind.WHEAT, 7);
		h.getLevel().setBlock(h.absolutePos(new BlockPos(7, -1, 5)), Blocks.DIRT.defaultBlockState(), 2 | 16); // the farmland under it is gone
		plantAt(h, 9, 5, CropKind.WHEAT, 7);
		h.getLevel().setBlock(h.absolutePos(new BlockPos(9, 0, 5)), Blocks.TORCHFLOWER_CROP.defaultBlockState(), 2);
		emptyFarmland(h, 11, 5);
		plantAt(h, 13, 5, CropKind.CARROTS, 3);
		h.getLevel().setBlock(h.absolutePos(new BlockPos(15, -1, 5)), Blocks.GRASS_BLOCK.defaultBlockState(), 2); // grass with air above: not farmland
		h.startSequence().thenIdle(2).thenExecute(() -> {
			LevelFieldView view = new LevelFieldView(h.getLevel());
			BlockPos a = h.absolutePos(new BlockPos(5, 0, 5));
			h.assertValueEqual(FieldCell.crop(CropKind.WHEAT, 7), view.cell(a.getX(), a.getY(), a.getZ()), "mature wheat on farmland");
			BlockPos b = h.absolutePos(new BlockPos(7, 0, 5));
			h.assertValueEqual(FieldCell.OTHER, view.cell(b.getX(), b.getY(), b.getZ()), "wheat on dirt is not a field cell");
			BlockPos c = h.absolutePos(new BlockPos(9, 0, 5));
			h.assertValueEqual(FieldCell.OTHER, view.cell(c.getX(), c.getY(), c.getZ()), "a torchflower crop is not a crop of the farmer");
			BlockPos d = h.absolutePos(new BlockPos(11, 0, 5));
			h.assertValueEqual(FieldCell.EMPTY, view.cell(d.getX(), d.getY(), d.getZ()), "empty farmland");
			BlockPos e = h.absolutePos(new BlockPos(13, 0, 5));
			h.assertValueEqual(FieldCell.crop(CropKind.CARROTS, 3), view.cell(e.getX(), e.getY(), e.getZ()), "carrots at 3");
			BlockPos g = h.absolutePos(new BlockPos(15, 0, 5));
			h.assertValueEqual(FieldCell.OTHER, view.cell(g.getX(), g.getY(), g.getZ()), "air over grass");
			h.assertTrue(view.reads <= 6 * 2, "at most two block reads per cell: " + view.reads);
			int far = a.getX() + 100_000;
			long before = view.reads;
			h.assertValueEqual(FieldCell.UNLOADED, view.cell(far, a.getY(), a.getZ()), "a far position is unloaded");
			h.assertValueEqual(before, view.reads, "an unloaded cell costs no block read");
			h.assertFalse(h.getLevel().hasChunk(far >> 4, a.getZ() >> 4), "reading it did not load the chunk");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_harvest", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void harvestReplantsAtomically(GameTestHelper h) {
		begin(h);
		Watcher w = new Watcher();
		h.onEachTick(() -> w.sample(h));
		Villager[] v = new Villager[1];
		List<BlockPos>[] cells = new List[1];
		long[] t0 = new long[1];
		long[] stat = new long[2];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			cells[0] = field(h, 8, 14, 10, 18, CropKind.WHEAT, 7, false);
			stat[0] = WorkStats.get("farmer", "harvested");
			stat[1] = WorkStats.get("farmer", "replanted");
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			w.start(h, cells[0], true);
			t0[0] = h.getTick();
		});
		double dist = 14;
		int bound = harvestBound(15, dist);
		seq = seq.thenWaitUntil(() -> {
			int harvested = (int) (WorkStats.get("farmer", "harvested") - stat[0]);
			h.assertValueEqual(15, harvested, "cells harvested so far; log " + FarmRuntime.get(h.getLevel().getServer()).unitLog().size());
		});
		seq = seq.thenIdle(5);
		seq.thenExecute(() -> {
			long took = h.getTick() - t0[0];
			w.stop();
			var units = FarmRuntime.get(h.getLevel().getServer()).unitLog().stream().filter(u -> u.unit().equals("HARVEST")).toList();
			int stationary = 0;
			StringBuilder gaps = new StringBuilder();
			for (int i = 1; i < units.size(); i++) {
				var a = units.get(i - 1);
				var b = units.get(i);
				double moved = Math.hypot(a.x() - b.x(), a.z() - b.z());
				if (moved < 0.3) {
					stationary++;
					long gap = b.tick() - a.tick();
					gaps.append(gap).append(' ');
					h.assertTrue(Math.abs(gap - 20) <= 1, "gap between two harvests without moving is " + gap + ", expected 20");
				}
			}
			log("harvestReplantsAtomically: 15 cells in " + took + " ticks (bound " + bound + "), " + stationary + " stationary gaps: " + gaps);
			h.assertTrue(took <= bound, "the patch took " + took + " ticks, bound " + bound);
			h.assertTrue(stationary >= 4, "at least 4 harvests in a row without moving: " + stationary);
			h.assertTrue(w.problems.isEmpty(), "atomic replant: " + w.problems);
			for (BlockPos c : cells[0]) {
				h.assertValueEqual(CropKind.WHEAT, cropAt(h, c), "wheat stands at " + c);
				h.assertTrue(ageAt(h, c) >= 0, "and has an age at " + c);
			}
			h.assertValueEqual(15L, total(h, v[0], Items.WHEAT), "exactly 15 wheat in the village (carried, stored, loose)");
			h.assertValueEqual(stat[0] + 15, WorkStats.get("farmer", "harvested"), "work.farmer.harvested");
			h.assertValueEqual(stat[1] + 15, WorkStats.get("farmer", "replanted"), "work.farmer.replanted");
			h.assertTrue(FarmRuntime.get(h.getLevel().getServer()).giveUps().isEmpty(), "no cell was given up: " + FarmRuntime.get(h.getLevel().getServer()).giveUps());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	/** Every farmland, crop and water block of the yard that is not inside the given column rectangle (a snapshot to compare later). */
	private static java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> farmBlocks(GameTestHelper h, int ex0, int ez0, int ex1, int ez1) {
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> out = new java.util.HashMap<>();
		for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) for (int y = -1; y <= 11; y++) {
			if (x >= ex0 && x <= ex1 && z >= ez0 && z <= ez1 && y <= 0) continue;
			BlockPos p = new BlockPos(x, y, z);
			var st = h.getBlockState(p);
			if (st.is(Blocks.FARMLAND) || st.is(Blocks.WATER) || st.getBlock() instanceof net.minecraft.world.level.block.CropBlock) out.put(p, st);
		}
		return out;
	}

	@GameTest(environment = "villageheart-gametest:farm_bigfield", structure = YARD, skyAccess = true, maxTicks = 25000)
	public void nineByNineWheatOneDay(GameTestHelper h) {
		begin(h);
		Watcher w = new Watcher();
		h.onEachTick(() -> w.sample(h));
		Villager[] v = new Villager[1];
		List<BlockPos>[] cells = new List[1];
		long[] t0 = new long[1];
		long[] reached = {-1, -1};
		long[] stat = new long[5];
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState>[] outside = new java.util.Map[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			cells[0] = field(h, 4, 13, 12, 21, CropKind.WHEAT, 7, true);
			h.assertValueEqual(80, cells[0].size(), "80 farmland cells");
			outside[0] = farmBlocks(h, 4, 13, 12, 21);
			stat[0] = WorkStats.get("farmer", "harvested");
			stat[1] = WorkStats.get("farmer", "wheat");
			stat[2] = WorkStats.get("farmer", "seeds_composted");
			stat[3] = WorkStats.get("farmer", "bone_meal_made");
			stat[4] = WorkStats.get("farmer", "replanted");
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			w.start(h, cells[0], true);
			t0[0] = h.getTick();
			h.onEachTick(() -> {
				long n = stored(h, "minecraft:wheat");
				if (reached[0] < 0 && n >= 60) reached[0] = h.getTick() - t0[0];
				if (reached[1] < 0 && n >= 80) reached[1] = h.getTick() - t0[0];
			});
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(stored(h, "minecraft:wheat") >= 80, "wheat in Storage: " + stored(h, "minecraft:wheat") + ", carried " + carried(v[0], Items.WHEAT)
				+ ", harvested " + (WorkStats.get("farmer", "harvested") - stat[0]) + " [" + inventory(v[0]) + "]"));
		seq.thenExecute(() -> {
			w.stop();
			log("nineByNineWheatOneDay: 60 wheat in Storage after " + reached[0] + " ticks, 80 after " + reached[1] + ", harvested "
					+ (WorkStats.get("farmer", "harvested") - stat[0]) + ", seeds composted " + (WorkStats.get("farmer", "seeds_composted") - stat[2])
					+ ", bone meal made " + (WorkStats.get("farmer", "bone_meal_made") - stat[3]) + ", used " + WorkStats.get("farmer", "bone_meal_used"));
			h.assertTrue(reached[0] >= 0 && reached[0] < 24000, "60 wheat in Storage before 24000 ticks: " + reached[0]);
			h.assertTrue(w.problems.isEmpty(), "no field cell was ever air; harvests match age 0: " + w.problems);
			for (BlockPos c : cells[0]) {
				h.assertValueEqual(CropKind.WHEAT, cropAt(h, c), "wheat stands at " + c);
				h.assertTrue(h.getBlockState(c.below()).is(Blocks.FARMLAND), "farmland under " + c);
			}
			h.assertValueEqual(outside[0], farmBlocks(h, 4, 13, 12, 21), "no crop, farmland or water outside the field changed");
			long harvested = WorkStats.get("farmer", "harvested") - stat[0];
			h.assertTrue(harvested >= 80, "work.farmer.harvested " + harvested);
			h.assertValueEqual(harvested, WorkStats.get("farmer", "replanted") - stat[4], "every harvested cell was replanted");
			h.assertValueEqual(WorkStats.get("farmer", "wheat") - stat[1], total(h, v[0], Items.WHEAT), "work.farmer.wheat = wheat carried + stored + loose (nothing lost)");
			h.assertTrue(WorkStats.get("farmer", "seeds_composted") - stat[2] >= 21, "seeds composted");
			h.assertTrue(WorkStats.get("farmer", "bone_meal_made") - stat[3] >= 1, "bone meal made");
			h.assertTrue(FarmRuntime.get(h.getLevel().getServer()).giveUps().isEmpty(), "no cell was given up: " + FarmRuntime.get(h.getLevel().getServer()).giveUps());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_immature", structure = YARD, skyAccess = true, maxTicks = 25000)
	public void immatureAndOutsideUntouched(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		CropKind[] kinds = {CropKind.WHEAT, CropKind.CARROTS, CropKind.POTATOES, CropKind.BEETROOTS};
		int[] young = {6, 6, 6, 2};
		int[] ripe = {7, 7, 7, 3};
		BlockPos[] youngCells = new BlockPos[4], ripeCells = new BlockPos[4];
		BlockPos edge = new BlockPos(9, 0, 29), out25 = new BlockPos(9, 0, 30), diag = new BlockPos(26, 0, 22), high = new BlockPos(20, 10, 14);
		List<String> problems = new ArrayList<>();
		long[] t0 = new long[1];
		long[] ticks = new long[2];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			for (int i = 0; i < 4; i++) {
				youngCells[i] = new BlockPos(4 + 2 * i, 0, 14);
				ripeCells[i] = new BlockPos(4 + 2 * i, 0, 16);
				plantAt(h, youngCells[i].getX(), youngCells[i].getZ(), kinds[i], young[i]);
				plantAt(h, ripeCells[i].getX(), ripeCells[i].getZ(), kinds[i], ripe[i]);
			}
			plantAt(h, edge.getX(), edge.getZ(), CropKind.WHEAT, 7);
			plantAt(h, out25.getX(), out25.getZ(), CropKind.WHEAT, 7);
			plantAt(h, diag.getX(), diag.getZ(), CropKind.WHEAT, 7);
			plantAt(h, high.getX(), 9, high.getZ(), CropKind.WHEAT, 7);
			for (BlockPos p : new BlockPos[] {edge, out25, diag, high}) {
				h.assertTrue(p.getX() >= 0 && p.getX() < 32 && p.getZ() >= 0 && p.getZ() < 32 && p.getY() < 28, "the cell " + p + " lies inside the yard");
				h.assertTrue(cropAt(h, p) == CropKind.WHEAT, "the test planted " + p);
			}
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			t0[0] = h.getTick();
		});
		// the five cells in the area are harvested and replanted
		BlockPos[] mustGo = {ripeCells[0] == null ? new BlockPos(4, 0, 16) : ripeCells[0], new BlockPos(6, 0, 16), new BlockPos(8, 0, 16), new BlockPos(10, 0, 16), edge};
		int bound = harvestBound(5, 30);
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : mustGo) h.assertTrue(ageAt(h, c) == 0, "cell " + c + " is replanted (age " + ageAt(h, c) + ")");
		});
		seq = seq.thenExecute(() -> {
			ticks[0] = h.getTick() - t0[0];
			log("immatureAndOutsideUntouched: the five cells in the area were harvested after " + ticks[0] + " ticks (bound " + bound + ")");
			h.assertTrue(ticks[0] <= bound, "took " + ticks[0] + ", bound " + bound);
		});
		// 24 000 ticks watched, sampled every 20 ticks; at tick 8000 the control cell ripens
		int total = 24000;
		for (int k = 0; k < total / 20; k++) {
			final int step = k;
			seq = seq.thenIdle(20).thenExecute(() -> {
				for (int i = 0; i < 4; i++) {
					if (step * 20 >= 8000 && i == 0) continue; // the control cell
					if (ageAt(h, youngCells[i]) != young[i] || cropAt(h, youngCells[i]) != kinds[i]) problems.add("tick " + step * 20 + ": young " + kinds[i] + " changed to " + ageAt(h, youngCells[i]));
				}
				for (BlockPos p : new BlockPos[] {out25, diag}) {
					if (ageAt(h, p) != 7 || !h.getBlockState(p.below()).is(Blocks.FARMLAND)) problems.add("tick " + step * 20 + ": outside " + p + " changed: " + h.getBlockState(p));
				}
				if (ageAt(h, new BlockPos(high.getX(), 10, high.getZ())) != 7) problems.add("tick " + step * 20 + ": the cell at dy +9 changed");
				if (carried(v[0], Items.BONE_MEAL) != 0 || stored(h, "minecraft:bone_meal") != 0) problems.add("tick " + step * 20 + ": bone meal appeared");
				if (step * 20 == 8000) {
					h.assertTrue(dev.villageheart.core.worker.DaySchedule.phaseAt(h.getLevel().getDayTime()) == dev.villageheart.core.worker.DayPhase.WORK,
							"the control happens in the work phase: day time " + h.getLevel().getDayTime());
					plantAt(h, youngCells[0].getX(), youngCells[0].getZ(), kinds[0], 7);
					ticks[1] = h.getTick();
				}
				if (step * 20 > 8000 && step * 20 <= 8400 && ageAt(h, youngCells[0]) == 0 && ticks[1] > 0) {
					ticks[1] = -(h.getTick() - ticks[1]) - 1; // negative: harvested after this many ticks
				}
			});
		}
		seq.thenExecute(() -> {
			log("immatureAndOutsideUntouched: control cell harvested " + (ticks[1] < 0 ? (-ticks[1] - 1) : -1) + " ticks after it ripened");
			h.assertTrue(problems.isEmpty(), "untouched cells changed: " + problems);
			h.assertTrue(ticks[1] < 0, "the control cell was harvested within 400 ticks of ripening");
			h.assertTrue(FarmRuntime.get(h.getLevel().getServer()).giveUps().isEmpty(), "no cell was given up: " + FarmRuntime.get(h.getLevel().getServer()).giveUps());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	private static int composterLevel(GameTestHelper h) { return h.getBlockState(composter()).getValue(net.minecraft.world.level.block.ComposterBlock.LEVEL); }

	@GameTest(environment = "villageheart-gametest:farm_compost", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void compostsSurplusSeedsExactly(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		long[] stat = new long[2];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			stat[0] = WorkStats.get("farmer", "seeds_composted");
			stat[1] = WorkStats.get("farmer", "bone_meal_made");
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[0].getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 41));
			t0[0] = h.getTick();
		});
		int bound = farmBound(26, dev.villageheart.core.Balance.FARM_TICKS_COMPOST, 2) + 20;
		seq = seq.thenWaitUntil(() -> h.assertTrue(WorkStats.get("farmer", "seeds_composted") - stat[0] == 25, "seeds composted: " + (WorkStats.get("farmer", "seeds_composted") - stat[0])
				+ " level " + composterLevel(h) + " [" + inventory(v[0]) + "]"));
		seq = seq.thenExecute(() -> {
			long took = h.getTick() - t0[0];
			log("compostsSurplusSeedsExactly: 25 seeds in " + took + " ticks (bound " + bound + ")");
			h.assertTrue(took <= bound, "took " + took + ", bound " + bound);
			h.assertValueEqual(16, carried(v[0], Items.WHEAT_SEEDS), "the reserve of 16 seeds is kept");
			h.assertValueEqual(1, carried(v[0], Items.BONE_MEAL), "one bone meal");
			h.assertValueEqual(1, composterLevel(h), "composter level after 25 seeds (21 + 4)");
			h.assertValueEqual(stat[1] + 1, WorkStats.get("farmer", "bone_meal_made"), "work.farmer.bone_meal_made");
			// phase 2: a ready composter is emptied
			h.setBlock(composter(), Blocks.COMPOSTER.defaultBlockState().setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL, 8));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the ready composter is emptied", () ->
				h.assertValueEqual(2, carried(v[0], Items.BONE_MEAL), "bone meal carried; composter level " + composterLevel(h)));
		seq.thenExecute(() -> {
			h.assertValueEqual(0, composterLevel(h), "the composter is empty again");
			h.assertValueEqual(16, carried(v[0], Items.WHEAT_SEEDS), "seeds untouched in phase 2");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_bonemeal", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void boneMealsYoungCrops(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos[] wheat = new BlockPos[6];
		BlockPos[] beet = {new BlockPos(14, 0, 18), new BlockPos(16, 0, 18)};
		long[] stat = new long[1];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			for (int i = 0; i < 6; i++) {
				wheat[i] = new BlockPos(4 + i, 0, 16);
				plantAt(h, wheat[i].getX(), wheat[i].getZ(), CropKind.WHEAT, 0);
			}
			for (BlockPos b : beet) plantAt(h, b.getX(), b.getZ(), CropKind.BEETROOTS, 0);
			stat[0] = WorkStats.get("farmer", "bone_meal_used");
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[0].getInventory().addItem(new ItemStack(Items.BONE_MEAL, 4));
			t0[0] = h.getTick();
		});
		int bound = farmBound(4, dev.villageheart.core.Balance.FARM_TICKS_BONEMEAL, 14);
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, carried(v[0], Items.BONE_MEAL), "bone meal left"));
		seq = seq.thenExecute(() -> {
			long took = h.getTick() - t0[0];
			int changed = 0;
			for (BlockPos c : wheat) {
				int age = ageAt(h, c);
				if (age != 0) {
					changed++;
					h.assertTrue(age >= 2 && age <= 5, "a bone meal gives 2 to 5 ages, got " + age + " at " + c);
				}
			}
			log("boneMealsYoungCrops: 4 bone meal in " + took + " ticks (bound " + bound + "), wheat ages "
					+ java.util.Arrays.stream(wheat).map(c -> String.valueOf(ageAt(h, c))).toList());
			h.assertTrue(took <= bound, "took " + took + ", bound " + bound);
			h.assertValueEqual(4, changed, "exactly 4 wheat cells changed");
			for (BlockPos b : beet) h.assertValueEqual(0, ageAt(h, b), "beetroots untouched while wheat is young");
			h.assertValueEqual(stat[0] + 4, WorkStats.get("farmer", "bone_meal_used"), "work.farmer.bone_meal_used");
			// phase 2: no wheat left in the field, two bone meal go to the beetroots
			for (BlockPos c : wheat) h.setBlock(c, Blocks.STONE);
			v[0].getInventory().addItem(new ItemStack(Items.BONE_MEAL, 2));
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, carried(v[0], Items.BONE_MEAL), "bone meal left"));
		seq.thenExecute(() -> {
			long took = h.getTick() - t0[0];
			log("boneMealsYoungCrops: phase 2 in " + took + " ticks, beetroot ages " + ageAt(h, beet[0]) + ", " + ageAt(h, beet[1]));
			h.assertTrue(took <= farmBound(2, dev.villageheart.core.Balance.FARM_TICKS_BONEMEAL, 14), "phase 2 took " + took);
			var units = FarmRuntime.get(h.getLevel().getServer()).unitLog().stream().filter(u -> u.unit().equals("BONEMEAL")).toList();
			h.assertValueEqual(6, units.size(), "six bone meal units in all");
			for (int i = 4; i < 6; i++) {
				BlockPos at = units.get(i).cell();
				h.assertTrue(at.equals(h.absolutePos(beet[0])) || at.equals(h.absolutePos(beet[1])), "bone meal " + i + " went to a beetroot, not " + at);
			}
			for (BlockPos b : beet) h.assertTrue(ageAt(h, b) >= 0 && ageAt(h, b) <= 2, "a beetroot gains 0 or 1 age per bone meal: " + ageAt(h, b));
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_plant", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void plantsEmptyFarmlandFromChest(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos[] carrotCentres = new BlockPos[3], wheatCentres = new BlockPos[3];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			for (int i = 0; i < 3; i++) {
				carrotCentres[i] = new BlockPos(1 + 4 * i, 0, 13);
				wheatCentres[i] = new BlockPos(1 + 4 * i, 0, 17);
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dz == 0) {
						emptyFarmland(h, carrotCentres[i].getX(), carrotCentres[i].getZ());
						emptyFarmland(h, wheatCentres[i].getX(), wheatCentres[i].getZ());
					} else {
						plantAt(h, carrotCentres[i].getX() + dx, carrotCentres[i].getZ() + dz, CropKind.CARROTS, 3);
						plantAt(h, wheatCentres[i].getX() + dx, wheatCentres[i].getZ() + dz, CropKind.WHEAT, 3);
					}
				}
			}
			StockpileTestSupport.fill(h, chest(), new ItemStack(Items.CARROT, 5), new ItemStack(Items.WHEAT_SEEDS, 10));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
		});
		long[] t0 = new long[1];
		seq = seq.thenExecute(() -> t0[0] = h.getTick());
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : carrotCentres) h.assertTrue(cropAt(h, c) == CropKind.CARROTS, "carrots planted at " + c);
			for (BlockPos c : wheatCentres) h.assertTrue(cropAt(h, c) == CropKind.WHEAT, "wheat planted at " + c);
		});
		seq = seq.thenExecute(() -> {
			long took = h.getTick() - t0[0];
			int bound = farmBound(6, dev.villageheart.core.Balance.FARM_TICKS_PLANT, 20) + 6 * 60;
			log("plantsEmptyFarmlandFromChest: 6 cells planted in " + took + " ticks (bound " + bound + ")");
			h.assertTrue(took <= bound, "took " + took + ", bound " + bound);
			for (BlockPos c : carrotCentres) h.assertValueEqual(0, ageAt(h, c), "carrots age 0 at " + c);
			for (BlockPos c : wheatCentres) h.assertValueEqual(0, ageAt(h, c), "wheat age 0 at " + c);
			var chest = StockpileTestSupport.container(h, chest());
			int carrots = 0, seeds = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.CARROT)) carrots += chest.getItem(i).getCount();
				if (chest.getItem(i).is(Items.WHEAT_SEEDS)) seeds += chest.getItem(i).getCount();
			}
			h.assertValueEqual(2, carrots, "the chest lost exactly 3 carrots");
			h.assertValueEqual(7, seeds, "and 3 seeds");
			h.assertValueEqual(0, carried(v[0], Items.CARROT) + carried(v[0], Items.WHEAT_SEEDS), "nothing taken that was not planted");
			// phase 2: an empty cell, nothing to plant with
			var c2 = StockpileTestSupport.container(h, chest());
			for (int i = 0; i < c2.getContainerSize(); i++) c2.setItem(i, ItemStack.EMPTY);
			c2.setChanged();
			emptyFarmland(h, 20, 20);
			t0[0] = h.getTick();
		});
		for (int k = 0; k < 60; k++) {
			seq = seq.thenIdle(20).thenExecute(() -> {
				h.assertTrue(h.getBlockState(new BlockPos(20, 0, 20)).isAir(), "the cell stays empty without a seed");
				h.assertTrue(status(h, v[0]) != dev.villageheart.core.worker.WorkerStatus.NO_WORK, "waiting for a seed is not NO_WORK");
			});
		}
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	private static List<Long> stationaryGaps(GameTestHelper h, List<BlockPos> relCells) {
		java.util.Set<BlockPos> abs = new java.util.HashSet<>();
		for (BlockPos r : relCells) abs.add(h.absolutePos(r));
		var units = FarmRuntime.get(h.getLevel().getServer()).unitLog().stream()
				.filter(u -> u.unit().equals("HARVEST") && abs.contains(u.cell())).toList();
		List<Long> gaps = new ArrayList<>();
		for (int i = 1; i < units.size(); i++) {
			var a = units.get(i - 1);
			var b = units.get(i);
			if (Math.hypot(a.x() - b.x(), a.z() - b.z()) < 0.3) gaps.add(b.tick() - a.tick());
		}
		return gaps;
	}

	@GameTest(environment = "villageheart-gametest:farm_hoe", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void hoeSpeedAndWear(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		List<String> problems = new ArrayList<>();
		long[] stat = new long[1];
		List<BlockPos>[] patchA = new List[1], patchB = new List[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			patchA[0] = field(h, 8, 14, 10, 18, CropKind.WHEAT, 7, false);
			StockpileTestSupport.fill(h, chest(), hoe("stone"));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			stat[0] = WorkStats.get("farmer", "harvested");
			h.onEachTick(() -> {
				var st = status(h, v[0]);
				if (st == dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL) problems.add("NEEDS_TOOL at " + h.getTick());
			});
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(WorkStats.get("farmer", "harvested") - stat[0] >= 15, "harvests so far " + (WorkStats.get("farmer", "harvested") - stat[0])));
		seq = seq.thenExecute(() -> {
			List<Long> gaps = stationaryGaps(h, patchA[0]);
			log("hoeSpeedAndWear: stone hoe gaps " + gaps + ", damage " + StockpileTestSupport.container(h, chest()).getItem(0).getDamageValue());
			h.assertTrue(gaps.size() >= 4, "stationary gaps: " + gaps);
			for (long g : gaps) h.assertTrue(Math.abs(g - 16) <= 1, "with a stone hoe two harvests are 16 ticks apart, not " + g);
			h.assertValueEqual(15, StockpileTestSupport.container(h, chest()).getItem(0).getDamageValue(), "the stone hoe lost one durability per harvest");
			// phase 2: the stone hoe goes, a golden hoe with 1 durability left comes, and a second patch ripens
			var c = StockpileTestSupport.container(h, chest());
			c.setItem(0, ItemStack.EMPTY);
			ItemStack gold = hoe("gold");
			gold.setDamageValue(gold.getMaxDamage() - 1);
			c.setItem(1, gold);
			c.setChanged();
			patchB[0] = field(h, 14, 14, 16, 18, CropKind.WHEAT, 7, false);
			stat[0] = WorkStats.get("farmer", "harvested");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(WorkStats.get("farmer", "harvested") - stat[0] >= 15, "harvests of the second patch " + (WorkStats.get("farmer", "harvested") - stat[0])));
		seq.thenExecute(() -> {
			List<Long> gaps = stationaryGaps(h, patchB[0]);
			log("hoeSpeedAndWear: after the golden hoe broke, gaps " + gaps);
			h.assertTrue(StockpileTestSupport.container(h, chest()).getItem(1).isEmpty(), "the golden hoe broke on its first harvest");
			h.assertTrue(gaps.size() >= 4, "stationary gaps: " + gaps);
			for (long g : gaps) h.assertTrue(Math.abs(g - 20) <= 1, "without a hoe two harvests are 20 ticks apart, not " + g);
			h.assertTrue(problems.isEmpty(), "a missing hoe is never a problem: " + problems);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_keep", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void depositKeepsSeedsAndFood(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		BlockPos camp2 = new BlockPos(8, 0, 14);
		long[] stat = new long[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			assertRandomTicksOff(h);
			sweepStrayCrops(h, 40);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, FARM, 1);
			buildStorage(h);
			BuildingTestSupport.room(h, camp2, 5, 4);
			h.setBlock(BuildingTestSupport.inside(camp2, 0, 0, 0), dev.villageheart.block.JobBlocks.BLOCKS.get(dev.villageheart.core.building.JobBlockType.CHOPPING_BLOCK));
			h.setBlock(BuildingTestSupport.inside(camp2, 4, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(camp2, 2, 0, 3), Blocks.TORCH);
			placeMarkers(h, true);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.LUMBER_CAMP, BuildingTestSupport.markerPos(camp2, 5), net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> {
			assertValid(h, true);
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(camp2, 5), dev.villageheart.block.MarkerStatus.VALID);
		});
		seq = seq.thenExecute(() -> {
			h.getLevel().setDayTime(3000);
			v[0] = hire(h, spawnSpot());
			v[1] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(camp2, 1, 0, 1));
			var r = dev.villageheart.worker.Workers.hire(h.getLevel(), v[1], dev.villageheart.core.worker.JobType.LUMBERJACK, java.util.Optional.empty());
			h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "the lumberjack control was hired: " + r);
			var inv = v[0].getInventory();
			inv.addItem(new ItemStack(Items.WHEAT, 64));
			inv.addItem(new ItemStack(Items.CARROT, 40));
			inv.addItem(new ItemStack(Items.POTATO, 30));
			inv.addItem(new ItemStack(Items.WHEAT_SEEDS, 50));
			inv.addItem(new ItemStack(Items.BONE_MEAL, 5));
			inv.addItem(new ItemStack(Items.BREAD, 2));
			v[1].getInventory().addItem(new ItemStack(Items.BREAD, 5));
			v[1].getInventory().addItem(new ItemStack(Items.OAK_LOG, 64));
			stat[0] = WorkStats.get("farmer", "seeds_composted");
			// the food table of the keep rule is vanilla's
			for (var e : dev.villageheart.core.job.FoodKeep.POINTS.entrySet()) {
				h.assertValueEqual(e.getValue(), Villager.FOOD_POINTS.getOrDefault(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.getKey())), -1), "food points of " + e.getKey());
			}
			h.assertValueEqual(dev.villageheart.core.Balance.WORKER_FOOD_KEEP, Villager.BREEDING_FOOD_THRESHOLD, "the food kept is vanilla's breeding threshold");
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertValueEqual(64L, stored(h, "minecraft:wheat"), "wheat in Storage");
			h.assertValueEqual(64L, stored(h, "minecraft:oak_log"), "logs in Storage");
			h.assertValueEqual(24L, stored(h, "minecraft:carrot"), "carrots in Storage");
			h.assertValueEqual(14L, stored(h, "minecraft:potato"), "potatoes in Storage");
			h.assertValueEqual(4L, stored(h, "minecraft:bread"), "bread in Storage: 2 from the farmer and 2 from the lumberjack");
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(16, carried(v[0], Items.WHEAT_SEEDS), "seeds left after composting [" + inventory(v[0]) + "]"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(16, carried(v[0], Items.CARROT), "the farmer kept 16 carrots");
			h.assertValueEqual(16, carried(v[0], Items.POTATO), "the farmer kept 16 potatoes");
			h.assertValueEqual(6, carried(v[0], Items.BONE_MEAL), "5 bone meal and the one it made");
			h.assertValueEqual(0, carried(v[0], Items.BREAD), "no bread: the kept carrots and potatoes are enough food");
			h.assertValueEqual(4, composterLevel(h), "21 seeds made a bone meal, 13 more gave level 4");
			h.assertValueEqual(stat[0] + 34, WorkStats.get("farmer", "seeds_composted"), "work.farmer.seeds_composted");
			h.assertValueEqual(3, carried(v[1], Items.BREAD), "the lumberjack kept 3 bread (12 food points)");
			h.assertValueEqual(0, carried(v[1], Items.OAK_LOG), "and handed in its logs");
			// phase 2: 200 seeds, one stack stays
			v[0].getInventory().clearContent();
			for (int i = 0; i < 3; i++) v[0].getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 64));
			v[0].getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(136L, stored(h, "minecraft:wheat_seeds"), "seeds in Storage [" + inventory(v[0]) + "] status " + status(h, v[0])));
		seq.thenExecute(() -> {
			h.assertTrue(carried(v[0], Items.WHEAT_SEEDS) <= 64, "at most one stack of seeds is kept: " + carried(v[0], Items.WHEAT_SEEDS));
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Where the loose items of one kind lie (relative position, distance to the farmer): for failure messages. */
	private static String looseDescription(GameTestHelper h, net.minecraft.world.item.Item item, Villager v) {
		StringBuilder sb = new StringBuilder("loose ").append(item).append(": ");
		for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(60.0))) {
			if (e.getItem().is(item)) sb.append(e.getItem().getCount()).append(" at ").append(e.blockPosition().subtract(h.absolutePos(BlockPos.ZERO))).append(" age ").append(e.getAge())
					.append(" dist ").append(Math.round(e.distanceTo(v) * 10) / 10.0).append(" blacklisted ").append(FarmRuntime.get(h.getLevel().getServer()).itemBlacklisted(e.getUUID(), h.getLevel().getGameTime())).append("; ");
		}
		return sb.append("farmer at ").append(v.blockPosition().subtract(h.absolutePos(BlockPos.ZERO))).append(" [").append(inventory(v)).append("]").toString();
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_full", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void storageFullStopsHarvest(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		List<BlockPos>[] cells = new List[2];
		long[] wheatBefore = new long[1];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			StockpileTestSupport.fillAll(h, storeChest1(), new ItemStack(Items.COBBLESTONE, 64));
			StockpileTestSupport.fillAll(h, storeChest2(), new ItemStack(Items.COBBLESTONE, 64));
			cells[0] = field(h, 8, 14, 10, 16, CropKind.WHEAT, 7, false);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[0].getInventory().addItem(new ItemStack(Items.WHEAT, 64));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the worker reports Storage full", () ->
				h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.STORAGE_FULL, status(h, v[0]), "status"));
		for (int k = 0; k < 60; k++) {
			seq = seq.thenIdle(20).thenExecute(() -> {
				for (BlockPos c : cells[0]) h.assertValueEqual(7, ageAt(h, c), "no crop is broken while Storage is full: " + c);
			});
		}
		seq = seq.thenExecute(() -> {
			var c = StockpileTestSupport.container(h, storeChest1());
			for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, ItemStack.EMPTY);
			c.setChanged();
			t0[0] = h.getTick();
		});
		int bound = farmBound(9, dev.villageheart.core.Balance.FARM_TICKS_HARVEST, 12) + 300;
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : cells[0]) h.assertTrue(ageAt(h, c) == 0, "cell " + c + " harvested and replanted (age " + ageAt(h, c) + ")");
		});
		seq = seq.thenExecute(() -> {
			long took = h.getTick() - t0[0];
			log("storageFullStopsHarvest: freed Storage, 9 cells harvested after " + took + " ticks (bound " + bound + ")");
			h.assertTrue(took <= bound, "took " + took + ", bound " + bound);
			h.assertTrue(stored(h, "minecraft:wheat") >= 64, "the 64 wheat went to Storage: " + stored(h, "minecraft:wheat"));
		});
		// phase 2: the inventory is full of things the farmer keeps; the replant must not cost a seed
		seq = seq.thenExecute(() -> {
			v[0].getInventory().clearContent();
			wheatBefore[0] = total(h, v[0], Items.WHEAT);
			for (int i = 0; i < v[0].getInventory().getContainerSize(); i++) v[0].getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			dev.villageheart.worker.farm.FarmJob.testKeepAll = i -> i == Items.COBBLESTONE;
			cells[1] = field(h, 14, 14, 16, 14, CropKind.WHEAT, 7, false);
		});
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : cells[1]) h.assertTrue(ageAt(h, c) == 0, "cell " + c + " replanted with a full inventory (age " + ageAt(h, c) + ")");
		});
		seq = seq.thenExecute(() -> {
			for (BlockPos c : cells[1]) h.assertValueEqual(CropKind.WHEAT, cropAt(h, c), "wheat stands again at " + c);
			h.assertTrue(loose(h, Items.WHEAT) >= 3, "the wheat lies on the ground, it did not fit: " + loose(h, Items.WHEAT));
			dev.villageheart.worker.farm.FarmJob.testKeepAll = i -> false; // now the cobblestone may go to Storage
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(wheatBefore[0] + 3, total(h, v[0], Items.WHEAT) - 0, "wheat in the village after the next trip; loose " + loose(h, Items.WHEAT)
				+ " [" + inventory(v[0]) + "]"));
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, loose(h, Items.WHEAT), "the popped wheat was collected; " + looseDescription(h, Items.WHEAT, v[0])));
		seq.thenExecute(() -> {
			h.assertTrue(FarmRuntime.get(h.getLevel().getServer()).giveUps().isEmpty(), "no cell was given up: " + FarmRuntime.get(h.getLevel().getServer()).giveUps());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_two", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void twoFarmersOneField(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Watcher w = new Watcher();
		h.onEachTick(() -> w.sample(h));
		List<BlockPos>[] cells = new List[1];
		List<String> problems = new ArrayList<>();
		GameTestSequence seq = scene(h, 2, true).thenExecute(() -> {
			cells[0] = field(h, 4, 14, 8, 18, CropKind.CARROTS, 7, false);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[1] = hire(h, BuildingTestSupport.inside(FARM, 3, 0, 1));
			w.start(h, cells[0], true);
			h.onEachTick(() -> {
				var claims = FarmRuntime.get(h.getLevel().getServer()).claims();
				if (new java.util.HashSet<>(claims.values()).size() > 2) problems.add("more claimants than farmers at " + h.getTick());
			});
		});
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : cells[0]) h.assertTrue(ageAt(h, c) == 0, "cell " + c + " replanted (age " + ageAt(h, c) + ")");
		});
		seq.thenExecute(() -> {
			w.stop();
			var units = FarmRuntime.get(h.getLevel().getServer()).unitLog().stream().filter(u -> u.unit().equals("HARVEST")).toList();
			java.util.Map<BlockPos, java.util.Set<java.util.UUID>> by = new java.util.HashMap<>();
			java.util.Map<java.util.UUID, Integer> per = new java.util.HashMap<>();
			for (var u : units) {
				by.computeIfAbsent(u.cell(), k -> new java.util.HashSet<>()).add(u.worker());
				per.merge(u.worker(), 1, Integer::sum);
			}
			log("twoFarmersOneField: " + units.size() + " harvests, per farmer " + per.values());
			h.assertValueEqual(25, units.size(), "25 harvests: no cell harvested twice");
			h.assertValueEqual(25, by.size(), "25 different cells");
			for (var e : by.entrySet()) h.assertValueEqual(1, e.getValue().size(), "cell " + e.getKey() + " was harvested by exactly one farmer");
			h.assertValueEqual(2, per.size(), "both farmers worked");
			for (int n : per.values()) h.assertTrue(n >= 3, "each farmer harvested a fair share: " + per);
			h.assertTrue(w.problems.isEmpty(), "no cell was ever air: " + w.problems);
			h.assertTrue(problems.isEmpty(), "claims: " + problems);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-12 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_other", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void otherPlantsUntouched(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos control = new BlockPos(6, 0, 14);
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> others = new java.util.LinkedHashMap<>();
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			plantAt(h, control.getX(), control.getZ(), CropKind.WHEAT, 7);
			BlockPos torch = new BlockPos(10, 0, 20), pitcher = new BlockPos(12, 0, 20), melon = new BlockPos(14, 0, 20), pumpkin = new BlockPos(16, 0, 20);
			BlockPos berry = new BlockPos(18, 0, 20), wart = new BlockPos(20, 0, 20);
			for (BlockPos p : new BlockPos[] {torch, pitcher, melon, pumpkin}) emptyFarmland(h, p.getX(), p.getZ());
			h.getLevel().setBlock(h.absolutePos(torch), Blocks.TORCHFLOWER_CROP.defaultBlockState().setValue(net.minecraft.world.level.block.TorchflowerCropBlock.AGE, 1), 2);
			h.getLevel().setBlock(h.absolutePos(pitcher), Blocks.PITCHER_CROP.defaultBlockState().setValue(net.minecraft.world.level.block.PitcherCropBlock.AGE, 2), 2);
			h.getLevel().setBlock(h.absolutePos(melon), Blocks.MELON_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7), 2);
			h.getLevel().setBlock(h.absolutePos(pumpkin), Blocks.PUMPKIN_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7), 2);
			h.getLevel().setBlock(h.absolutePos(berry), Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3), 2);
			h.getLevel().setBlock(h.absolutePos(wart.below()), Blocks.SOUL_SAND.defaultBlockState(), 2);
			h.getLevel().setBlock(h.absolutePos(wart), Blocks.NETHER_WART.defaultBlockState().setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 3), 2);
			for (BlockPos p : new BlockPos[] {torch, pitcher, melon, pumpkin, berry, wart}) others.put(p, h.getBlockState(p));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, ageAt(h, control), "the control wheat is harvested and replanted"));
		for (int k = 0; k < 300; k++) {
			seq = seq.thenIdle(20).thenExecute(() -> {
				for (var e : others.entrySet()) h.assertValueEqual(e.getValue(), h.getBlockState(e.getKey()), "the other plant at " + e.getKey() + " is untouched");
			});
		}
		seq.thenExecute(() -> {
			h.assertValueEqual(6, others.size(), "six other plants watched");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-13 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_reload", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void interruptAndReload(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[3];
		Watcher w = new Watcher();
		h.onEachTick(() -> w.sample(h));
		List<BlockPos>[] cells = new List[2];
		long[] stat = new long[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			cells[0] = field(h, 4, 14, 8, 18, CropKind.WHEAT, 7, false);
			h.getLevel().setDayTime(1000);
			stat[0] = WorkStats.get("farmer", "harvested");
			v[0] = hire(h, spawnSpot());
			w.start(h, cells[0], true);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(WorkStats.get("farmer", "harvested") - stat[0] >= 6, "harvests " + (WorkStats.get("farmer", "harvested") - stat[0])));
		seq = seq.thenExecute(() -> {
			int young = 0, ripe = 0, empty = 0;
			for (BlockPos c : cells[0]) {
				int a = ageAt(h, c);
				if (a == 0) young++;
				else if (a == 7) ripe++;
				else empty++;
			}
			h.assertValueEqual(6, young, "six cells were replanted when the farmer is fired");
			h.assertValueEqual(19, ripe, "and 19 still stand ripe");
			h.assertValueEqual(0, empty, "none is half handled");
			h.assertTrue(dev.villageheart.worker.Workers.fire(h.getLevel(), v[0]), "the farmer is fired");
			v[1] = hire(h, spawnSpot());
		});
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : cells[0]) h.assertTrue(ageAt(h, c) == 0, "cell " + c + " is replanted by the new farmer (age " + ageAt(h, c) + ")");
		});
		seq = seq.thenExecute(() -> {
			w.stop();
			h.assertTrue(w.problems.isEmpty(), "no cell was ever air: " + w.problems);
			cells[1] = field(h, 4, 22, 8, 26, CropKind.WHEAT, 7, false);
			stat[0] = WorkStats.get("farmer", "harvested");
			w.start(h, cells[1], true);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(WorkStats.get("farmer", "harvested") - stat[0] >= 6, "harvests of the second field " + (WorkStats.get("farmer", "harvested") - stat[0])));
		seq = seq.thenExecute(() -> {
			v[2] = LumberTestSupport.reloadEntity(h.getLevel(), v[1]);
			FarmRuntime.resetForTests();
			dev.villageheart.worker.job.WorkerStatuses.clearAll();
		});
		seq = seq.thenWaitUntil(() -> {
			for (BlockPos c : cells[1]) h.assertTrue(ageAt(h, c) == 0, "cell " + c + " is replanted by the reloaded farmer (age " + ageAt(h, c) + ")");
		});
		seq.thenExecute(() -> {
			w.stop();
			h.assertTrue(w.problems.isEmpty(), "no cell was ever air: " + w.problems);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- extra: items resting on farmland --------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:farm_collect", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void collectsItemsOnFarmland(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos[] spots = {new BlockPos(8, 0, 14), new BlockPos(10, 0, 14), new BlockPos(12, 0, 14)};
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			for (BlockPos p : spots) emptyFarmland(h, p.getX(), p.getZ());
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			// an item that rests on farmland has its feet 1/16 below the crop cell: blockPosition() names the farmland block
			for (BlockPos p : spots) h.spawnItem(Items.WHEAT, p);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the three wheat on the farmland are picked up", () ->
				h.assertValueEqual(3, carried(v[0], Items.WHEAT), "wheat carried; loose " + loose(h, Items.WHEAT)));
		seq.thenExecute(() -> {
			h.assertValueEqual(0, loose(h, Items.WHEAT), "nothing is left lying");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- extra: a deposit trip during a COLLECT -----------------------------------------------------

	/** The trip's own start check runs first (its tick comes before the planner's), so the planner never says YIELD. */
	@GameTest(environment = "villageheart-gametest:farm_trip_first", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void collectSurvivesTripThatStartsFirst(GameTestHelper h) {
		collectAcrossATrip(h, true);
	}

	/** The planner sees the wanted trip first and yields. */
	@GameTest(environment = "villageheart-gametest:farm_trip_planner", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void collectSurvivesTripThatPlannerYieldsTo(GameTestHelper h) {
		collectAcrossATrip(h, false);
	}

	/**
	 * A wheat item far from the farmer (no field), the farmer walks to it; mid-walk its inventory fills (a trip is now wanted) at a tick
	 * chosen so that the trip starts first or the planner yields first. The trip takes longer than the 400-tick collect timeout, and the
	 * item must still be collected afterwards (it used to be blacklisted for 6000 ticks on the way back).
	 */
	private static void collectAcrossATrip(GameTestHelper h, boolean tripFirst) {
		begin(h);
		Villager[] v = new Villager[1];
		long[] filledAt = new long[1];
		GameTestSequence seq = scene(h, 1, true).thenExecute(() -> {
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			h.spawnItem(Items.WHEAT, new BlockPos(4, 0, 27));
		});
		seq = seq.thenIdle(90).thenWaitUntil(() -> {
			long next = h.getLevel().getGameTime() + 1;
			boolean tripCheck = (next + v[0].getId()) % 20 == 0;
			h.assertTrue(carried(v[0], Items.WHEAT) == 0 && loose(h, Items.WHEAT) == 1, "still on its way to the item");
			if (tripFirst) h.assertTrue(tripCheck, "wait for a tick where the trip check comes first");
			else {
				h.assertTrue(!tripCheck, "wait for a tick where the trip check does not run");
				FarmRuntime.get(h.getLevel().getServer()).forcePlanForTest(v[0].getUUID()); // the planner runs next tick, the trip check later
			}
			for (int i = 0; i < v[0].getInventory().getContainerSize(); i++) v[0].getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			filledAt[0] = next;
		});
		seq = seq.thenIdle(450).thenExecute(() -> {
			log("collectAcrossATrip(tripFirst=" + tripFirst + "): inventory filled at " + filledAt[0] + ", now " + h.getLevel().getGameTime() + " [" + inventory(v[0]) + "]");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1500, "the wheat is collected after the trip", () ->
				h.assertValueEqual(1L, carried(v[0], Items.WHEAT) + stored(h, "minecraft:wheat"), "wheat carried or stored; " + looseDescription(h, Items.WHEAT, v[0])));
		seq.thenExecute(() -> {
			h.assertValueEqual(0, loose(h, Items.WHEAT), "nothing is left lying; " + looseDescription(h, Items.WHEAT, v[0]));
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
