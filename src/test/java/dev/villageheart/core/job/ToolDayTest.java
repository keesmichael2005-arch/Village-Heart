package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Balance;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.job.ToolDay.Evening;
import dev.villageheart.core.job.ToolDay.Morning;
import dev.villageheart.core.worker.WorkerStatus;
import org.junit.jupiter.api.Test;

/** Evening the tool goes home, morning it comes back out, and a death sends it home while the cargo drops. */
class ToolDayTest {
	@Test
	void needsChestIsAppended() {
		assertEquals(WorkerStatus.NEEDS_CHEST, WorkerStatus.values()[WorkerStatus.values().length - 1]);
		assertEquals(ActivityKind.NEEDS_CHEST, ActivityKind.values()[ActivityKind.values().length - 1]);
	}

	@Test
	void morningTakesTheToolAndEveningPutsItBack() {
		assertEquals(Morning.TAKE, ToolDay.morning(false, true));
		assertEquals(Morning.ALREADY, ToolDay.morning(true, true));
		assertEquals(Morning.NONE, ToolDay.morning(false, false));
		assertEquals(Evening.RETURN_THEN_DUMP, ToolDay.evening(true, true));
		assertEquals(Evening.STAY_THEN_DUMP, ToolDay.evening(true, false));
		assertEquals(Evening.DUMP, ToolDay.evening(false, true));
	}

	@Test
	void deathSendsTheToolHomeAndDropsTheCargo() {
		ToolDay.Death d = ToolDay.onDeath(true);
		assertTrue(d.toolToChest());
		assertTrue(d.dropCargo());
		assertFalse(ToolDay.onDeath(false).toolToChest());
	}

	@Test
	void aNightGuardHandsGearInAtDawnAndADayGuardAtDusk() {
		assertTrue(ToolDay.guardHandsIn(Shift.NIGHT, 0));
		assertFalse(ToolDay.guardHandsIn(Shift.NIGHT, Balance.WORK_START + 100));
		assertTrue(ToolDay.guardHandsIn(Shift.DAY, Balance.GUARD_DUTY_TICKS));
		assertFalse(ToolDay.guardHandsIn(Shift.DAY, Balance.WORK_START));
	}
}
