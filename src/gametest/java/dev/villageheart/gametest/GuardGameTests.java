package dev.villageheart.gametest;

import static dev.villageheart.gametest.GuardTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.guard.Shift;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.guard.GuardSystem;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Layer 2: guards (M18). Rules: docs/TESTING.md "Guard tests (M18+)": own environment per test ({@code guards_<name>}, natural spawning
 * off), structure {@code yard} with sky access, scene first, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last.
 */
public class GuardGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	// ---- T-L2-2 ------------------------------------------------------------------------------------

	/** A day guard is on duty at noon, not asleep; the night guard of the same Barracks is asleep in its bed. */
	@GameTest(environment = "villageheart-gametest:guards_day_guard_noon", structure = YARD, skyAccess = true, maxTicks = 1600)
	public void dayGuardOnDutyAtNoon(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[2];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			setTime(h, 5800);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			g[1] = hireGuard(h, spawnSpot().east(), Optional.of(Shift.NIGHT));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the day guard is on duty and the night guard asleep", () -> {
			h.assertTrue(g[0].getBrain().isActive(GuardActivities.DUTY), "the day guard is on GUARD_DUTY at " + h.getLevel().getDayTime());
			h.assertTrue(!g[0].isSleeping(), "the day guard is awake");
			h.assertTrue(g[1].isSleeping(), "the night guard sleeps (activity rest: " + g[1].getBrain().isActive(Activity.REST) + ")");
		});
		seq.thenExecute(() -> {
			h.assertTrue(g[1].getBrain().isActive(Activity.REST), "the night guard's activity is REST");
			System.out.println("[m18] dayGuardOnDutyAtNoon: settled " + (h.getTick() - t0[0]) + " ticks after hire");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ------------------------------------------------------------------------------------

	/** The Barracks bed is held by the mod: an unemployed villager sleeps in the House bed, never in the guard's bed. */
	@GameTest(environment = "villageheart-gametest:guards_bed_reserved", structure = YARD, skyAccess = true, maxTicks = 3600)
	public void barracksBedReserved(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			NeedsTestSupport.buildHouse(h, 1);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, NeedsTestSupport.houseMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertTrue(WorkerTestSupport.poiPresent(h, NeedsTestSupport.houseBedHead(0)), "the House bed has a POI");
			h.assertTrue(freeTickets(h, bedHead(0)) == 0, "the mod holds the Barracks bed's ticket");
		}).thenExecute(() -> {
			setTime(h, 12500);
			v[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(BARRACKS, 3, 0, 1));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2400, "the unemployed villager takes the House bed", () ->
				h.assertTrue(NeedsTestSupport.homeIs(h, v[0], NeedsTestSupport.houseBedHead(0)), "HOME is the House bed, got "
						+ v[0].getBrain().getMemory(MemoryModuleType.HOME)));
		seq.thenIdle(300).thenExecute(() -> {
			h.assertTrue(freeTickets(h, bedHead(0)) == 0, "the Barracks bed still has no free ticket");
			h.assertTrue(!v[0].getSleepingPos().filter(p -> p.equals(h.absolutePos(bedHead(0)))).isPresent(), "never slept in the Barracks bed");
			System.out.println("[m18] barracksBedReserved: House bed taken " + (h.getTick() - t0[0]) + " ticks after the spawn");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Control for {@link #barracksBedReserved}: without the Barracks (marker removed) the same bed is free and the villager takes it. */
	@GameTest(environment = "villageheart-gametest:guards_bed_control", structure = YARD, skyAccess = true, maxTicks = 3600)
	public void barracksBedControlFreeWithoutMarker(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenWaitUntil(() -> h.assertTrue(freeTickets(h, bedHead(0)) == 0, "held while the Barracks stands"))
				.thenExecute(() -> {
					h.setBlock(barracksMarker(), net.minecraft.world.level.block.Blocks.AIR);
					setTime(h, 12500);
					v[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(BARRACKS, 3, 0, 1));
				});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2400, "the villager takes the freed Barracks bed", () ->
				h.assertTrue(NeedsTestSupport.homeIs(h, v[0], bedHead(0)), "HOME is the old Barracks bed, got " + v[0].getBrain().getMemory(MemoryModuleType.HOME)));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	static long freeTickets(GameTestHelper h, BlockPos headRel) {
		BlockPos head = h.absolutePos(headRel);
		var poi = h.getLevel().getPoiManager();
		return poi.getInSquare(t -> true, head, 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				.filter(r -> r.getPos().equals(head)).count();
	}
}
