package dev.villageheart.core.food;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DinnerClockTest {
	@Test void dueOncePerDayFromEleven() {
		assertFalse(DinnerClock.due(5 * 24000L + 10999, 4));
		assertTrue(DinnerClock.due(5 * 24000L + 11000, 4));
		assertFalse(DinnerClock.due(5 * 24000L + 11000, 5));
		assertFalse(DinnerClock.due(5 * 24000L + 23999, 5));
		assertTrue(DinnerClock.due(6 * 24000L + 11000, 5));
		assertFalse(DinnerClock.due(6 * 24000L + 10999, 5));
	}

	@Test void timeSetBackwardsStillServes() {
		assertTrue(DinnerClock.due(11000, 37));
		assertTrue(DinnerClock.due(11000, 1));
		assertFalse(DinnerClock.due(11000, 0));
	}

	@Test void negativeDayTimeFloors() {
		assertEquals(-1, DinnerClock.dayIndex(-1));
		assertEquals(-1, DinnerClock.dayIndex(-24000));
		assertEquals(-2, DinnerClock.dayIndex(-24001));
		assertTrue(DinnerClock.due(-1, -2)); // phase 23999 of day -1
		assertFalse(DinnerClock.due(-1, -1));
		assertFalse(DinnerClock.due(-13001, -2)); // phase 10999
		assertTrue(DinnerClock.due(-13000, -2)); // phase 11000
	}

	@Test void initialLastDay() {
		assertEquals(0, DinnerClock.initialLastDay(13000));
		assertEquals(-1, DinnerClock.initialLastDay(5000));
		assertEquals(5, DinnerClock.initialLastDay(5 * 24000L + 11000));
		assertEquals(4, DinnerClock.initialLastDay(5 * 24000L + 10999));
	}
}
