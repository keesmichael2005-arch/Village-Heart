package dev.villageheart.core.worker;

import static dev.villageheart.core.worker.DayPhase.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class DayScheduleTest {
	@Test
	void boundaries() {
		assertEquals(WAKE, DaySchedule.phaseAt(0));
		assertEquals(WAKE, DaySchedule.phaseAt(999));
		assertEquals(WORK, DaySchedule.phaseAt(1000));
		assertEquals(WORK, DaySchedule.phaseAt(8999));
		assertEquals(MEET, DaySchedule.phaseAt(9000));
		assertEquals(MEET, DaySchedule.phaseAt(10999));
		assertEquals(GO_HOME, DaySchedule.phaseAt(11000));
		assertEquals(GO_HOME, DaySchedule.phaseAt(11999));
		assertEquals(SLEEP, DaySchedule.phaseAt(12000));
		assertEquals(SLEEP, DaySchedule.phaseAt(23999));
		assertEquals(WAKE, DaySchedule.phaseAt(24000));
		assertEquals(WORK, DaySchedule.phaseAt(25000));
	}

	@Test
	void negativeDayTimeWraps() {
		assertEquals(SLEEP, DaySchedule.phaseAt(-1));
		assertEquals(WORK, DaySchedule.phaseAt(-23000));
		assertEquals(WAKE, DaySchedule.phaseAt(Long.MIN_VALUE / 24000 * 24000));
	}

	@Test
	void valuesComeFromBalance() {
		assertEquals(WAKE, DaySchedule.phaseAt(Balance.WORK_START - 1));
		assertEquals(WORK, DaySchedule.phaseAt(Balance.WORK_START));
		assertEquals(WORK, DaySchedule.phaseAt(Balance.MEET_START - 1));
		assertEquals(MEET, DaySchedule.phaseAt(Balance.MEET_START));
		assertEquals(MEET, DaySchedule.phaseAt(Balance.HOME_START - 1));
		assertEquals(GO_HOME, DaySchedule.phaseAt(Balance.HOME_START));
		assertEquals(GO_HOME, DaySchedule.phaseAt(Balance.SLEEP_START - 1));
		assertEquals(SLEEP, DaySchedule.phaseAt(Balance.SLEEP_START));
		assertEquals(WAKE, DaySchedule.phaseAt(Balance.DAY_TICKS));
	}
}
