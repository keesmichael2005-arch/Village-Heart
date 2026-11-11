package dev.villageheart.core.guard;

import static dev.villageheart.core.guard.GuardPhase.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GuardScheduleTest {
	private static GuardPhase p(Shift s, long t) { return GuardSchedule.phase(s, t, false, false, true); }

	@Test
	void dayShiftBoundaries() {
		assertEquals(DUTY, p(Shift.DAY, 0));
		assertEquals(DUTY, p(Shift.DAY, 11999));
		assertEquals(OFF, p(Shift.DAY, 12000));
		assertEquals(OFF, p(Shift.DAY, 12999));
		assertEquals(SLEEP, p(Shift.DAY, 13000));
		assertEquals(SLEEP, p(Shift.DAY, 23999));
	}

	@Test
	void nightShiftBoundaries() {
		assertEquals(OFF, p(Shift.NIGHT, 0));
		assertEquals(OFF, p(Shift.NIGHT, 999));
		assertEquals(SLEEP, p(Shift.NIGHT, 1000));
		assertEquals(SLEEP, p(Shift.NIGHT, 6000));
		assertEquals(SLEEP, p(Shift.NIGHT, 11999));
		assertEquals(DUTY, p(Shift.NIGHT, 12000));
		assertEquals(DUTY, p(Shift.NIGHT, 18000));
	}

	@Test
	void dayTimeWraps() {
		assertEquals(SLEEP, p(Shift.NIGHT, 30000));
		assertEquals(DUTY, p(Shift.DAY, 24000));
	}

	@Test
	void overrides() {
		assertEquals(SLEEP, GuardSchedule.phase(Shift.NIGHT, 18000, true, true, true), "wounded beats raid");
		assertEquals(DUTY, GuardSchedule.phase(Shift.NIGHT, 6000, true, false, true), "raid calls up");
		assertEquals(OFF, GuardSchedule.phase(Shift.DAY, 100, false, false, false), "no work turns duty into off");
		assertEquals(SLEEP, GuardSchedule.phase(Shift.NIGHT, 6000, false, false, false), "but not sleep");
		assertEquals(OFF, GuardSchedule.phase(Shift.DAY, 12100, false, false, false));
	}

	@Test
	void alarmCallsEveryGuardUpButWoundedStaysInBed() {
		assertEquals(DUTY, GuardSchedule.phase(Shift.NIGHT, 6000, false, true, false, true), "asleep night guard");
		assertEquals(DUTY, GuardSchedule.phase(Shift.DAY, 13500, false, true, false, true), "asleep day guard");
		assertEquals(DUTY, GuardSchedule.phase(Shift.DAY, 12500, false, true, false, true), "off-shift guard");
		assertEquals(SLEEP, GuardSchedule.phase(Shift.NIGHT, 18000, false, true, true, true), "wounded beats the alarm");
		assertEquals(SLEEP, GuardSchedule.phase(Shift.DAY, 13500, false, false, false, true));
		assertEquals(DUTY, GuardSchedule.phase(Shift.DAY, 100, false, true, false, false), "the alarm is like a raid: it skips the cannot-work rule");
	}

	@Test
	void everyShiftHasTwelveThousandDutyTicks() {
		for (Shift s : Shift.values()) {
			int duty = 0, off = 0, sleep = 0;
			for (int t = 0; t < 24000; t++) {
				switch (p(s, t)) {
					case DUTY -> duty++;
					case OFF -> off++;
					case SLEEP -> sleep++;
				}
			}
			assertEquals(12000, duty);
			assertEquals(1000, off);
			assertEquals(11000, sleep);
		}
	}

	@Test
	void aTraineeIsNeverOnDutyNorSentToBedByTheClock() {
		for (Shift sh : Shift.values()) {
			for (long t = 0; t < 24000; t += 500) {
				assertEquals(OFF, GuardSchedule.phase(sh, t, false, false, false, true, true), sh + " at " + t);
				assertEquals(OFF, GuardSchedule.phase(sh, t, true, true, false, true, true), "not even a raid or the alarm calls a trainee: " + sh + " at " + t);
			}
		}
		assertEquals(SLEEP, GuardSchedule.phase(Shift.DAY, 5000, false, false, true, true, true), "wounded still comes first");
		// a qualified guard follows the clock as before
		assertEquals(DUTY, GuardSchedule.phase(Shift.NIGHT, 18000, false, false, false, true, false));
		assertEquals(SLEEP, GuardSchedule.phase(Shift.NIGHT, 5000, false, false, false, true, false));
	}
}
