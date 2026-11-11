package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RaiderRecallTest {
	@Test
	void aRaiderThatDoesNotCloseOnTheHeartIsBroughtToTheGate() {
		assertTrue(RaiderRecall.recall(true, 200, 40), "the ledge group never walks in");
		assertFalse(RaiderRecall.recall(true, 199, 40), "still inside the stall window");
		assertFalse(RaiderRecall.recall(true, 200, 24), "already at the gate");
		assertFalse(RaiderRecall.recall(false, 400, 80), "a dead raider is not moved");
	}

	@Test
	void theNearestGateIsTheOneTheRaiderIsBroughtTo() {
		int[] gates = { 681, 70, 870, 681, 70, 855 };
		assertArrayEquals(new int[] { 681, 70, 855 }, RaiderRecall.nearestGate(640, 70, 850, gates));
		assertNull(RaiderRecall.nearestGate(640, 70, 850, new int[0]));
	}

	@Test
	void theRaiderIsPlacedOutsideTheGateAwayFromTheHeart() {
		double[] at = RaiderRecall.outsideGate(681, 70, 855, 641, 821);
		assertTrue(at[0] > 681, "east of the east gate, not inside the ring");
		assertEquals(70, at[1], 0.01);
		assertTrue(Math.abs(at[2] - 855) < 4);
	}
}
