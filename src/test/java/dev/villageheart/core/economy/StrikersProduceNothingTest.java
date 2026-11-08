package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkGate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Roadmap M12 L1: strikers produce nothing. */
class StrikersProduceNothingTest {
	private static final Needs UNPAID = Needs.of(0, true, false);

	@Test void unpaidMeansSpeedZeroAndTheGateIsClosed() {
		assertEquals(0.0, UNPAID.speed());
		assertFalse(WorkGate.mayWork(UNPAID));
		assertTrue(WorkGate.mayWork(Needs.DEFAULT));
		assertFalse(WorkGate.mayWork(Needs.of(2, true, true)), "two missed dinners close it too");
		assertTrue(WorkGate.mayWork(Needs.of(1, false, true)), "half speed and unhoused still work");
	}

	@Test void everyJobsSpeedIsZeroWhenUnpaid() {
		for (JobType job : JobType.values()) {
			for (int level = 1; level <= 3; level++) {
				if (job.tool().isPresent()) {
					for (var tier : dev.villageheart.core.job.ToolTier.values()) {
						assertEquals(0.0, WorkSpeed.multiplier(Optional.of(tier), job.tool().get(), level, UNPAID), job + " L" + level + " " + tier);
						assertTrue(WorkSpeed.multiplier(Optional.of(tier), job.tool().get(), level, Needs.DEFAULT) > 0, "the control: " + job + " works when paid");
					}
					assertEquals(0.0, WorkSpeed.multiplier(Optional.empty(), job.tool().get(), level, UNPAID), job + " without a tool");
				}
				assertEquals(0.0, WorkSpeed.untooled(level, UNPAID), job + " untooled L" + level);
			}
		}
	}

	@Test void aClockAtSpeedZeroNeverCompletesAUnit() {
		WorkClock clock = new WorkClock(Balance.BASE_TICKS_PER_LOG);
		int units = 0;
		for (int t = 0; t < 24000; t++) units += clock.tick(WorkSpeed.untooled(3, UNPAID));
		assertEquals(0, units);
	}

	@Test void aPaidWorkerAtTheSameClockDoesWork() {
		WorkClock clock = new WorkClock(Balance.BASE_TICKS_PER_LOG);
		int units = 0;
		for (int t = 0; t < 24000; t++) units += clock.tick(WorkSpeed.untooled(1, Needs.DEFAULT));
		assertTrue(units > 0, "the control: the same clock at speed 1 completes units");
	}
}
