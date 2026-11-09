package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReservesTest {
	@Test
	void defaultAndRanges() {
		assertEquals(3, Reserves.DEFAULT.foodDays());
		assertEquals(64, Reserves.DEFAULT.itemReserve());
		assertTrue(Reserves.valid(0, 0));
		assertTrue(Reserves.valid(30, 1024));
		assertFalse(Reserves.valid(-1, 64));
		assertFalse(Reserves.valid(31, 64));
		assertFalse(Reserves.valid(3, -1));
		assertFalse(Reserves.valid(3, 1025));
	}

	@Test
	void constructorThrowsOutOfRangeAndClampedDoesNot() {
		assertThrows(IllegalArgumentException.class, () -> new Reserves(31, 64));
		assertThrows(IllegalArgumentException.class, () -> new Reserves(3, 1025));
		assertEquals(new Reserves(30, 1024), Reserves.clamped(99, 9999));
		assertEquals(new Reserves(0, 0), Reserves.clamped(-5, -5));
	}
}
