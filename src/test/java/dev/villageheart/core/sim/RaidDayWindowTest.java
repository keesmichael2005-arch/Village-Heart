package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RaidDayWindowTest {
	@Test
	void aReloadedRaidIsNotASecondStartAndDoesNotReplayItsDeaths() {
		long start = 68041;
		long day3End = 72040;
		long day4End = 96040;
		assertFalse(RaidDayWindow.contains(start, day3End, day4End));
		assertTrue(RaidDayWindow.contains(start, Long.MIN_VALUE, day4End), "a fresh meter would count the same raid again");
		assertArrayEquals(new int[] {5, 26}, RaidDayWindow.delta(start, 6, 35, start, 1, 9));
		assertArrayEquals(new int[] {1, 4}, RaidDayWindow.delta(90000, 1, 4, start, 6, 35), "a later raid reports its own totals");
	}
}
