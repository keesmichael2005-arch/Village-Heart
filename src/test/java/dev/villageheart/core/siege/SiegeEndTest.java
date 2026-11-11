package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SiegeEndTest {
	private static final int NIGHT_END = 23000;

	@Test
	void aRolledSiegeStopsWhenTheSwitchIsOff() {
		assertTrue(SiegeEnd.over(false, false, 18000, NIGHT_END, 1, 1, 0, 3, 1, 3));
	}

	@Test
	void aScriptedSiegeKeepsGoingWhileTheSwitchIsOff() {
		assertFalse(SiegeEnd.over(true, false, 18000, NIGHT_END, 1, 1, 0, 8, 1, 3));
		assertTrue(SiegeEnd.over(true, false, NIGHT_END, NIGHT_END, 1, 1, 0, 8, 1, 3), "dawn still ends it");
		assertTrue(SiegeEnd.over(true, false, 18000, NIGHT_END, 1, 2, 0, 8, 1, 3), "the next day still ends it");
		assertFalse(SiegeEnd.over(true, false, 18000, NIGHT_END, 1, 1, 8, 8, 2, 3), "groups still to spawn");
		assertTrue(SiegeEnd.over(true, false, 18000, NIGHT_END, 1, 1, 8, 8, 3, 3), "every zombie dead and every group out");
	}

	@Test
	void aRolledSiegeWithTheSwitchOnIsNotOverJustForThat() {
		assertFalse(SiegeEnd.over(false, true, 18000, NIGHT_END, 1, 1, 0, 3, 0, 3));
	}
}
