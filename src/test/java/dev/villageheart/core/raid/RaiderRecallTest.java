package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class RaiderRecallTest {
	@Test
	void aRaiderThatDoesNotCloseOnTheHeartIsBroughtToTheGate() {
		long stall = Balance.RAIDER_RECALL_STALL;
		assertTrue(RaiderRecall.recall(true, stall, 40, 0), "the ledge group never walks in");
		assertFalse(RaiderRecall.recall(true, stall, 40, 1), "one already on a guard is a fight");
		assertFalse(RaiderRecall.recall(true, stall - 1, 40, 0), "still inside the stall window");
		assertFalse(RaiderRecall.recall(true, stall, 24, 0), "already at the gate");
		assertFalse(RaiderRecall.recall(false, stall + 100, 80, 0), "a dead raider is not moved");
	}

	@Test
	void theNearestGateIsTheOneTheRaiderIsBroughtTo() {
		int[] gates = { 681, 70, 870, 681, 70, 855 };
		assertArrayEquals(new int[] { 681, 70, 855 }, RaiderRecall.nearestGate(640, 70, 850, gates));
		assertNull(RaiderRecall.nearestGate(640, 70, 850, new int[0]));
	}

	@Test
	void theRaiderIsPlacedBesideTheGuardTowardTheGate() {
		double[] at = RaiderRecall.besideGuard(660, 70, 864, 681, 855);
		double dx = at[0] - 660, dz = at[2] - 864;
		assertEquals(2, Math.sqrt(dx * dx + dz * dz), 0.05);
		assertTrue(at[0] > 660, "toward the east gate, two blocks from the guard");
		assertEquals(70, at[1], 0.01);
	}
}
