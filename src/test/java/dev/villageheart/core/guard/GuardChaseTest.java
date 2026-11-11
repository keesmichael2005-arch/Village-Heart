package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GuardChaseTest {
	@Test
	void aMovingChaseAroundTheWallStillCountsAsClosing() {
		assertTrue(GuardChase.detourCounts(true, true, true, true));
		assertFalse(GuardChase.detourCounts(false, true, true, true), "an ordinary fight still uses the straight-line give-up");
		assertFalse(GuardChase.detourCounts(true, false, true, true), "no path started");
		assertFalse(GuardChase.detourCounts(true, true, false, true), "the path has finished");
		assertFalse(GuardChase.detourCounts(true, true, true, false), "standing on the same block is a stall");
	}

	@Test
	void aRaiderKillInsideTheChaseMarginCounts() {
		assertTrue(GuardChase.killCounts(true, false));
		assertTrue(GuardChase.killCounts(false, true));
		assertFalse(GuardChase.killCounts(false, false), "a zombie outside the claim is not a chase kill");
	}
}
