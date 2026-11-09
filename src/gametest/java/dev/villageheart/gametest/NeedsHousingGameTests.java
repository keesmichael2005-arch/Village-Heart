package dev.villageheart.gametest;

import static dev.villageheart.gametest.NeedsTestSupport.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.needs.Diet;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.food.DietAttachments;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/** Layer 2: housing, re-homing, and the diet's life cycle (M11). Rules: see {@link NeedsTestSupport}. */
public class NeedsHousingGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	private static final BlockPos OUTSIDE_FOOT = new BlockPos(22, 0, 22);
	private static final BlockPos OUTSIDE_HEAD = OUTSIDE_FOOT.south();

	/** A refresh runs every 100 ticks: two refreshes plus slack. */
	private static final int REHOME_BOUND = 2 * Balance.NEEDS_REFRESH_TICKS + 20;

	// ---- T-L2-5 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_house_bed", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void workerPrefersHouseBed(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] w = new Villager[1];
		long[] t0 = new long[1];
		var seq = houseScene(h, 1).thenExecute(() -> {
			outsideBed(h, OUTSIDE_FOOT);
		}).thenWaitUntil(() -> h.assertTrue(WorkerTestSupport.poiPresent(h, OUTSIDE_HEAD), "the outside bed's POI exists"))
				.thenExecute(() -> {
					h.getLevel().setDayTime(2000);
					NeedsTestHooks.enable();
					w[0] = FarmTestSupport.hire(h, OUTSIDE_FOOT.west());
					claimBed(h, w[0], OUTSIDE_HEAD); // vanilla's AcquirePoi would pick the nearer bed too; here it is certain
					h.assertFalse(WorkerTestSupport.poiHasSpace(h, OUTSIDE_HEAD), "the worker holds the outside bed");
					t0[0] = h.getTick();
				});
		seq = BuildingTestSupport.awaitWithin(h, seq, REHOME_BOUND, "the worker moving into the House bed", () -> {
			h.assertTrue(homeIs(h, w[0], houseBedHead(0)), "HOME is the House bed; it is " + dev.villageheart.needs.Housing.home(w[0]));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(needs(w[0]).housed(), "needs.housed after the next refresh"))
				.thenExecute(() -> {
					NeedsTestSupport.log("workerPrefersHouseBed: housed after " + (h.getTick() - t0[0]) + " ticks (bound " + REHOME_BOUND + " + 100)");
					h.assertTrue(WorkerTestSupport.poiHasSpace(h, OUTSIDE_HEAD), "the outside bed's ticket was given back");
					h.assertFalse(WorkerTestSupport.poiHasSpace(h, houseBedHead(0)), "the House bed's ticket is taken");
					// the House loses its roof: invalid, so its bed no longer houses anybody
					h.setBlock(houseRoof(), Blocks.AIR);
					t0[0] = h.getTick();
				});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(2, 0) + Balance.NEEDS_REFRESH_TICKS, "needs.housed turning false",
				() -> h.assertFalse(needs(w[0]).housed(), "needs.housed after the House went invalid"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:needs_house_evict", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void workerEvictsAnAwakeResident(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] w = new Villager[1];
		Villager[] resident = new Villager[1];
		houseScene(h, 1).thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			NeedsTestHooks.enable();
			resident[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(HOUSE, 1, 0, 2));
			claimBed(h, resident[0], houseBedHead(0));
			w[0] = FarmTestSupport.hire(h, OUTSIDE_FOOT);
		}).thenWaitUntil(() -> {
			h.assertTrue(homeIs(h, w[0], houseBedHead(0)), "the worker took the only House bed");
		}).thenExecute(() -> {
			h.assertFalse(homeIs(h, resident[0], houseBedHead(0)), "the awake resident was moved off it");
			h.assertFalse(WorkerTestSupport.poiHasSpace(h, houseBedHead(0)), "exactly one ticket of the bed is taken");
		}).thenWaitUntil(() -> h.assertTrue(needs(w[0]).housed(), "needs.housed")).thenExecute(() -> {
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:needs_house_worker_bed", structure = YARD, skyAccess = true, maxTicks = 900)
	public void workersNeverTakeEachOthersBeds(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] a = new Villager[1];
		Villager[] b = new Villager[1];
		houseScene(h, 1, 2).thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			NeedsTestHooks.enable();
			a[0] = FarmTestSupport.hire(h, OUTSIDE_FOOT);
			claimBed(h, a[0], houseBedHead(0));
			b[0] = FarmTestSupport.hire(h, OUTSIDE_FOOT.east(2));
		}).thenIdle(2 * Balance.NEEDS_REFRESH_TICKS + 40).thenExecute(() -> {
			h.assertTrue(homeIs(h, a[0], houseBedHead(0)), "the first worker keeps its House bed");
			h.assertFalse(homeIs(h, b[0], houseBedHead(0)), "the second worker did not take it");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	// ---- T-L2-8 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_diet_reload", structure = YARD, skyAccess = true, maxTicks = 900)
	public void dietSurvivesReload(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[2];
		Villager[] back = new Villager[2];
		Diet[] before = new Diet[2];
		Needs[] unused = new Needs[1];
		houseScene(h, 1).thenExecute(() -> {
			NeedsTestHooks.enable();
			h.getLevel().setDayTime(2000);
			long claim = village(h).claimedAtGameTime();
			v[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(HOUSE, 1, 0, 2));
			DietAttachments.write(v[0], new Diet(claim, 2, 5, 0));
			v[1] = FarmTestSupport.hire(h, FarmTestSupport.spawnSpot());
			DietAttachments.write(v[1], new Diet(claim, 2, 5, 0));
			dev.villageheart.needs.NeedsSystem.mirror(h.getLevel().getServer(), village(h));
			before[0] = diet(h, v[0]);
			before[1] = diet(h, v[1]);
			h.assertValueEqual(2, needs(v[1]).hungryDays(), "the worker's needs before the reload");
			h.assertFalse(needs(v[1]).fed(), "not fed");
			Needs expected = needs(v[1]);
			back[0] = LumberTestSupport.reloadEntity(h.getLevel(), v[0]);
			back[1] = LumberTestSupport.reloadEntity(h.getLevel(), v[1]);
			h.assertValueEqual(before[0], diet(h, back[0]), "the diet of a villager after the entity reload");
			h.assertValueEqual(before[1], diet(h, back[1]), "the diet of a worker after the entity reload");
			h.assertValueEqual(expected, needs(back[1]), "the worker's needs after the entity reload");
		}).thenIdle(60).thenExecute(() -> {
			h.assertTrue(WorkerAttachments.isWorker(back[1]), "the reloaded worker is still a worker");
			h.assertValueEqual(before[1], diet(h, back[1]), "the diet is intact 60 ticks later");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	// ---- T-L2-9 -------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_hire_fire", structure = YARD, skyAccess = true, maxTicks = 900)
	public void hireAndFireKeepHunger(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] v = new Villager[1];
		storageHouseScene(h).thenExecute(() -> {
			NeedsTestHooks.enable();
			h.getLevel().setDayTime(2000);
			v[0] = WorkerTestSupport.spawnVillager(h, FarmTestSupport.spawnSpot());
			DietAttachments.write(v[0], new Diet(village(h).claimedAtGameTime(), 1, 5, 0));
			var r = Workers.hire(h.getLevel(), v[0], dev.villageheart.core.worker.JobType.FARMER, java.util.Optional.empty());
			h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "hired: " + r);
			h.assertValueEqual(Needs.of(1, false, true), needs(v[0]), "a hungry villager stays hungry when hired (and starts unhoused)");
			h.assertTrue(Workers.fire(h.getLevel(), v[0]), "fired");
			h.assertValueEqual(1, diet(h, v[0]).hungryDays(), "firing leaves the hunger alone");
			putBread(h, 10);
			WorkerTestSupport.spawnVillager(h, FarmTestSupport.spawnSpot().east()); // somebody else to eat with
			nextDinner(h);
		}).thenWaitUntil(() -> h.assertValueEqual(0, diet(h, v[0]).hungryDays(), "a meal ends the hunger"))
				.thenExecute(() -> {
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	/** Farmhouse, Storage (two chests) and the claim; stray villagers swept. */
	private static net.minecraft.gametest.framework.GameTestSequence storageHouseScene(GameTestHelper h) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			FarmTestSupport.assertRandomTicksOff(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, FarmTestSupport.FARM, 1);
			FarmTestSupport.buildStorage(h);
			FarmTestSupport.placeMarkers(h, true);
		}).thenWaitUntil(() -> FarmTestSupport.assertValid(h, true));
	}
}
