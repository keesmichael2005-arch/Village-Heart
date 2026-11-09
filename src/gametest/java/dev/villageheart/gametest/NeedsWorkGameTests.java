package dev.villageheart.gametest;

import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.NeedsTestSupport.*;

import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.needs.Diet;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.food.DietAttachments;
import dev.villageheart.needs.NeedsSystem;
import dev.villageheart.worker.farm.FarmRuntime;
import dev.villageheart.worker.job.WorkStats;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

/**
 * Layer 2: hunger and housing slow the jobs (M11). Each run starts at a fresh day's 1000 with today's dinner marked as served, so no dinner
 * falls inside it (asserted at the end); the diet is set by the test and mirrored at once.
 */
public class NeedsWorkGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static void setDiet(GameTestHelper h, Villager v, int hungryDays) {
		DietAttachments.write(v, new Diet(village(h).claimedAtGameTime(), hungryDays, 0, 0));
		NeedsSystem.mirror(h.getLevel().getServer(), village(h));
	}

	/** Day time to today's 1000, today's dinner counted as served. Returns the day index. */
	private static long morning(GameTestHelper h) {
		long day = dev.villageheart.core.food.DinnerClock.dayIndex(h.getLevel().getDayTime());
		h.getLevel().setDayTime(day * 24000L + 1000);
		dev.villageheart.world.VillageClaims.data(h.getLevel().getServer()).setLastDinnerDay(day);
		return day;
	}

	// ---- T-L2-6 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_lumber", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void hungryLumberjackSlower(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		LumberTestSupport.Watcher w = new LumberTestSupport.Watcher();
		h.onEachTick(() -> w.sample(h));
		BlockPos[] spots = {new BlockPos(3, 0, 12), new BlockPos(3, 0, 20), new BlockPos(3, 0, 28)};
		@SuppressWarnings("unchecked") Set<BlockPos>[] logs = new Set[3];
		Villager[] v = new Villager[1];
		long[] day = new long[1];
		int[] total = new int[1];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			sweepVillagers(h);
			buildHouse(h, 1);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, houseMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertTrue(WorkerTestSupport.poiPresent(h, houseBedHead(0)), "the House bed's POI exists");
		}).thenExecute(() -> {
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			NeedsTestHooks.enable();
			day[0] = morning(h);
			v[0] = hire(h, spawnSpot());
		}).thenWaitUntil(() -> h.assertTrue(needs(v[0]).housed(), "the lumberjack sleeps in the House bed (needs.housed)"));

		// phase 1: one missed dinner, housed: 80 ticks per log
		seq = seq.thenExecute(() -> {
			BlockPos base = growOak(h, spots[0], 2L);
			logs[0] = logsOf(h, base);
			setDiet(h, v[0], 1);
			h.assertValueEqual(0.5, needs(v[0]).speed(), "speed with one missed dinner");
			w.start(h, logs[0]);
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[0]), "logs of tree 0 left"));
		seq = seq.thenExecute(() -> {
			var gaps = w.gaps();
			double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(-1);
			NeedsTestSupport.log("hungryLumberjackSlower hungry 1: " + logs[0].size() + " logs, gaps " + gaps + ", mean " + mean);
			h.assertTrue(gaps.size() >= 2, "enough logs to measure: " + gaps);
			h.assertTrue(Math.abs(mean - 80.0) <= 1.0, "mean gap " + mean + ", expected 80");
			// phase 2: two missed dinners, speed 0
			BlockPos base = growOak(h, spots[1], 3L);
			logs[1] = logsOf(h, base);
			total[0] = logs[1].size();
			setDiet(h, v[0], 2);
			h.assertValueEqual(0.0, needs(v[0]).speed(), "speed with two missed dinners");
			h.assertValueEqual(WorkerStatus.TOO_HUNGRY, status(h, v[0]), "status with two missed dinners");
			w.start(h, logs[1]);
		});
		seq = seq.thenIdle(900).thenExecute(() -> {
			h.assertValueEqual(total[0], standing(h, logs[1]), "no log of tree 1 fell in 900 ticks of hunger");
			h.assertValueEqual(WorkerStatus.TOO_HUNGRY, status(h, v[0]), "still TOO_HUNGRY");
			setDiet(h, v[0], 0); // it has eaten
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, fellBound(1, 30, ToolTier.STONE), "a log falling once fed",
				() -> h.assertTrue(standing(h, logs[1]) < total[0], "a log of tree 1 fell"));
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[1]), "logs of tree 1 left"));

		// phase 3: fed but unhoused (the House bed is gone): 40 / 0.75 = 53.3 ticks per log
		seq = seq.thenExecute(() -> {
			h.setBlock(houseBedFoot(0), net.minecraft.world.level.block.Blocks.AIR);
			h.setBlock(houseBedHead(0), net.minecraft.world.level.block.Blocks.AIR);
			BlockPos base = growOak(h, spots[2], 4L);
			logs[2] = logsOf(h, base);
			w.start(h, logs[2]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 0) + 100, "needs.housed turning false",
				() -> h.assertFalse(needs(v[0]).housed(), "needs.housed after the bed went"));
		seq = seq.thenExecute(() -> w.start(h, logs[2]));
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[2]), "logs of tree 2 left"));
		seq.thenExecute(() -> {
			var gaps = w.gaps();
			double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(-1);
			NeedsTestSupport.log("hungryLumberjackSlower unhoused: " + logs[2].size() + " logs, gaps " + gaps + ", mean " + mean);
			h.assertTrue(gaps.size() >= 2, "enough logs to measure: " + gaps);
			h.assertTrue(Math.abs(mean - 40.0 / 0.75) <= 1.0, "mean gap " + mean + ", expected 53.3");
			h.assertValueEqual(day[0], lastDinnerDay(h), "no dinner fell inside the run");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	// ---- T-L2-7 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_farm", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void hungryFarmerSlower(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[1];
		@SuppressWarnings("unchecked") List<BlockPos>[] cells = new List[1];
		long[] day = new long[1];
		long[] stat = new long[2];
		long[] t0 = new long[1];
		GameTestSequence seq = FarmTestSupport.scene(h, 1, true).thenExecute(() -> {
			sweepVillagers(h);
			buildHouse(h, 1);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, houseMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertTrue(WorkerTestSupport.poiPresent(h, houseBedHead(0)), "the House bed's POI exists");
		}).thenExecute(() -> {
			NeedsTestHooks.enable();
			day[0] = morning(h);
			v[0] = FarmTestSupport.hire(h, FarmTestSupport.spawnSpot());
		}).thenWaitUntil(() -> h.assertTrue(needs(v[0]).housed(), "the farmer sleeps in the House bed (needs.housed)"));
		// two missed dinners first: nothing is harvested
		seq = seq.thenExecute(() -> {
			cells[0] = FarmTestSupport.field(h, 20, 14, 22, 18, CropKind.WHEAT, 7, false);
			stat[0] = WorkStats.get("farmer", "harvested");
			setDiet(h, v[0], 2);
			h.assertValueEqual(WorkerStatus.TOO_HUNGRY, FarmTestSupport.status(h, v[0]), "status with two missed dinners");
		}).thenIdle(600).thenExecute(() -> {
			h.assertValueEqual(stat[0], WorkStats.get("farmer", "harvested"), "nothing harvested in 600 ticks of hunger");
			setDiet(h, v[0], 1); // one missed dinner: half speed
			stat[1] = FarmRuntime.get(h.getLevel().getServer()).unitLog().size();
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(15L, WorkStats.get("farmer", "harvested") - stat[0], "cells harvested; "
				+ FarmRuntime.get(h.getLevel().getServer()).unitLog().size()));
		seq.thenExecute(() -> {
			var units = FarmRuntime.get(h.getLevel().getServer()).unitLog().stream().skip(stat[1]).filter(u -> u.unit().equals("HARVEST")).toList();
			int stationary = 0;
			StringBuilder gaps = new StringBuilder();
			List<Long> all = new java.util.ArrayList<>();
			for (int i = 1; i < units.size(); i++) {
				var a = units.get(i - 1);
				var b = units.get(i);
				if (Math.hypot(a.x() - b.x(), a.z() - b.z()) < 0.3) {
					stationary++;
					long gap = b.tick() - a.tick();
					gaps.append(gap).append(' ');
					all.add(gap);
				}
			}
			// 20 work units at speed 0.5 are 40 ticks; the planner and a one-tick step between two cells add up to 2 more (measured: 40-42).
			// A speed bug would show as 20 (ignored) or 80 (zero).
			double mean = all.stream().mapToLong(Long::longValue).average().orElse(-1);
			h.assertTrue(all.stream().allMatch(g -> g >= 39 && g <= 45), "every stationary gap is 40 plus at most a few ticks: " + all);
			h.assertTrue(mean >= 39.5 && mean <= 43, "mean stationary gap " + mean + ", expected about 40 (20 / 0.5): " + all);
			NeedsTestSupport.log("hungryFarmerSlower: 15 cells in " + (h.getTick() - t0[0]) + " ticks, " + stationary + " stationary gaps: " + gaps);
			h.assertTrue(stationary >= 3, "at least 3 harvests in a row without moving: " + stationary);
			h.assertValueEqual(day[0], lastDinnerDay(h), "no dinner fell inside the run");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}
}
