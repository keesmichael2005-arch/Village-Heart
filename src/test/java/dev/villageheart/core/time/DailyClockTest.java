package dev.villageheart.core.time;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class DailyClockTest {
	private static final DailyClock WAGES = new DailyClock(Balance.WAGE_TICK);
	private static final DailyClock DINNER = new DailyClock(Balance.DINNER_TICK);

	@Test void wageClockIsDueOnEveryDayIndexChangeAndNotTwiceInADay() {
		assertTrue(WAGES.due(24000, 0));
		assertFalse(WAGES.due(24000, 1));
		assertFalse(WAGES.due(24000 + 23999, 1));
		assertTrue(WAGES.due(2 * 24000, 1));
		assertFalse(WAGES.due(5, 0));
	}

	@Test void aJumpOverThreeDaysIsDueOnceThenNot() {
		assertTrue(WAGES.due(4 * 24000L + 10, 1));
		assertFalse(WAGES.due(4 * 24000L + 11, DailyClock.dayIndex(4 * 24000L + 10)));
	}

	@Test void aBackwardJumpIsDueOnceToo() {
		assertTrue(WAGES.due(1000, 5)); // /time set 1000 from day 5: index 0
		assertFalse(WAGES.due(1001, 0));
	}

	@Test void initialLastDayIsToday() {
		assertEquals(3, WAGES.initialLastDay(3 * 24000L + 7000));
		assertEquals(0, WAGES.initialLastDay(0));
	}

	@Test void dayIndexFloorsNegativeTimes() {
		assertEquals(-1, DailyClock.dayIndex(-1));
		assertEquals(-2, DailyClock.dayIndex(-24001));
	}

	@Test void dinnerClockKeepsM11Behaviour() {
		assertFalse(DINNER.due(5 * 24000L + 10999, 4));
		assertTrue(DINNER.due(5 * 24000L + 11000, 4));
		assertEquals(4, DINNER.initialLastDay(5 * 24000L + 10999));
		assertEquals(5, DINNER.initialLastDay(5 * 24000L + 11000));
		assertTrue(DINNER.due(11000, 37));
	}

	@Test void eventTickIsRangeChecked() {
		assertThrows(IllegalArgumentException.class, () -> new DailyClock(-1));
		assertThrows(IllegalArgumentException.class, () -> new DailyClock(24000));
	}
}
