package dev.villageheart.core.hunt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-8 */
class TripDayTest {
	@Test
	void sameDayKeepsTheCount() {
		assertEquals(new TripDay(3, 2), TripDay.of(3, 2, 3));
	}

	@Test
	void anotherDayStartsAtZeroEarlierOrLater() {
		assertEquals(new TripDay(4, 0), TripDay.of(3, 2, 4));
		assertEquals(new TripDay(1, 0), TripDay.of(3, 2, 1));
		assertEquals(new TripDay(0, 0), TripDay.of(-1, 0, 0));
	}

	@Test
	void plusOne() {
		assertEquals(new TripDay(3, 3), TripDay.of(3, 2, 3).plusOne());
		assertEquals(new TripDay(5, 1), TripDay.of(-1, 0, 5).plusOne());
	}
}
