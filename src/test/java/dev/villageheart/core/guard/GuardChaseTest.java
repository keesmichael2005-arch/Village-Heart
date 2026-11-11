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
	void aChaseThatStopsAtTheWallWalksToTheGateNearestTheRaider() {
		int[] gates = { 681, 70, 870, 681, 70, 855 };
		assertArrayEquals(new int[] { 681, 70, 855 },
				GuardChase.gateWaypoint(660, 70, 864, 637, 70, 858, gates, false),
				"the west raider's nearest gate is the east gate at z 855");
		assertNull(GuardChase.gateWaypoint(681, 70, 855, 637, 70, 858, gates, false), "already at that gate");
		assertNull(GuardChase.gateWaypoint(660, 70, 864, 637, 70, 858, gates, true), "a path that reaches needs no gate");
		assertNull(GuardChase.gateWaypoint(660, 70, 864, 637, 70, 858, new int[0], false));
		assertNull(GuardChase.gateWaypoint(660, 70, 864, 660, 70, 980, gates, false),
				"a raider at the spawn ring is walked toward, not sent back to a gate");
	}

	@Test
	void aRaiderOnALedgeThePathCannotReachIsPulledDown() {
		assertTrue(GuardChase.pullDown(true, false, 20, 14));
		assertFalse(GuardChase.pullDown(true, true, 20, 14), "a path that reaches needs no pull");
		assertFalse(GuardChase.pullDown(false, false, 20, 14), "an ordinary fight does not pull");
		assertFalse(GuardChase.pullDown(true, false, 40, 14), "a far raider is walked toward");
		assertFalse(GuardChase.pullDown(true, false, 20, 2), "a step up is not a ledge");
	}

	@Test
	void aRaiderKillInsideTheChaseMarginCounts() {
		assertTrue(GuardChase.killCounts(true, false));
		assertTrue(GuardChase.killCounts(false, true));
		assertFalse(GuardChase.killCounts(false, false), "a zombie outside the claim is not a chase kill");
	}
}
