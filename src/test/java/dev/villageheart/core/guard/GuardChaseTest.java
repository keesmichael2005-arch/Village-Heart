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
	}

	@Test
	void aPathThatStopsAtTheWallUsesTheGateAndALongStepDoesNot() {
		assertTrue(GuardChase.useGate(false, 70, 65), "five blocks into the wall is not progress");
		assertFalse(GuardChase.useGate(false, 100, 50), "a partial path that closes 50 blocks is worth walking");
		assertFalse(GuardChase.useGate(true, 70, 0), "a path that reaches needs no gate");
	}

	@Test
	void aRaiderThePathCannotReachAndThatIsAlreadyCloseIsPulledIn() {
		assertTrue(GuardChase.pullClose(true, false, 20));
		assertTrue(GuardChase.pullClose(true, false, 18), "the hole under the Heart is close enough");
		assertFalse(GuardChase.pullClose(true, true, 20), "a path that reaches needs no pull");
		assertFalse(GuardChase.pullClose(false, false, 20), "an ordinary fight does not pull");
		assertFalse(GuardChase.pullClose(true, false, 40), "a far raider is walked toward");
	}

	@Test
	void theGuardStepsToACloseRaiderAndDoesNotBringItInsideWhileLeaving() {
		assertTrue(GuardChase.stepToRaider(true, false, 20, false), "the guard goes to the raider");
		assertFalse(GuardChase.stepToRaider(true, false, 20, true), "a guard still walking to the gate does not pull the raider in");
		assertFalse(GuardChase.stepToRaider(true, true, 20, false));
		assertFalse(GuardChase.stepToRaider(true, false, 40, false));
	}

	@Test
	void aRaiderKillInsideTheChaseMarginCounts() {
		assertTrue(GuardChase.killCounts(true, false));
		assertTrue(GuardChase.killCounts(false, true));
		assertFalse(GuardChase.killCounts(false, false), "a zombie outside the claim is not a chase kill");
	}
}
