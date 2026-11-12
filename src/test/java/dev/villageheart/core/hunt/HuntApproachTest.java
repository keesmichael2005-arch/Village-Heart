package dev.villageheart.core.hunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Balance;
import dev.villageheart.core.hunt.HuntApproach.Choice;
import org.junit.jupiter.api.Test;

/**
 * A hunter pushed against another villager is not "the ground is unreachable". That mis-label waits {@link Balance#HUNTER_RETRY_TICKS}
 * (600) and is what blew the 450-tick bound in {@code twoHuntersOneGround}.
 */
class HuntApproachTest {
	private static final double CLOSEST = Balance.HUNT_ENTRY_CLOSEST;

	@Test
	void aMovingNeighbourNearTheMarkerArrivesEvenWhenTheWalkGivesUp() {
		assertEquals(Choice.ARRIVE, HuntApproach.choose(6.0, CLOSEST, true, true, true, 0));
		assertEquals(Choice.ARRIVE, HuntApproach.choose(3.0, CLOSEST, true, true, false, 0));
	}

	@Test
	void aBystanderNearTheMarkerDoesNotCountAsArrived() {
		assertEquals(Choice.WALK, HuntApproach.choose(3.0, CLOSEST, false, true, false, 0), "a still or NoAI villager is not a hunter who is out");
		assertEquals(Choice.YIELD, HuntApproach.choose(3.0, CLOSEST, false, true, true, 0));
		assertEquals(Choice.UNREACHABLE, HuntApproach.choose(3.0, CLOSEST, false, true, true, 1));
	}

	@Test
	void aJamFartherOutYieldsOnceInsteadOfTheLongRetry() {
		assertEquals(Choice.YIELD, HuntApproach.choose(12.0, CLOSEST, true, true, true, 0));
		assertEquals(Choice.YIELD, HuntApproach.choose(12.0, CLOSEST, false, true, true, 0));
		assertEquals(Choice.UNREACHABLE, HuntApproach.choose(12.0, CLOSEST, false, false, true, 0));
		assertEquals(Choice.WALK, HuntApproach.choose(12.0, CLOSEST, false, false, false, 0));
	}

	@Test
	void aRepeatedGiveUpIsAnUnreachableGroundEvenWithANeighbour() {
		assertEquals(Choice.UNREACHABLE, HuntApproach.choose(12.0, CLOSEST, false, true, true, HuntApproach.MAX_JAM_GIVE_UPS));
		assertEquals(Choice.UNREACHABLE, HuntApproach.choose(12.0, CLOSEST, true, true, true, HuntApproach.MAX_JAM_GIVE_UPS + 3), "far from the marker a mover is not an arrival");
	}

	@Test
	void theHigherIdYieldsAfterAShortJamAndNotWhenItCanAlreadyGoOut() {
		assertFalse(HuntApproach.yieldNow(29, true, true, false, 12.0, CLOSEST));
		assertTrue(HuntApproach.yieldNow(30, true, true, false, 12.0, CLOSEST));
		assertFalse(HuntApproach.yieldNow(30, true, false, false, 12.0, CLOSEST), "the lower id keeps the path");
		assertFalse(HuntApproach.yieldNow(30, false, true, false, 12.0, CLOSEST));
		assertFalse(HuntApproach.yieldNow(30, true, true, false, 6.0, CLOSEST), "close enough to go out beside them");
	}

	@Test
	void aWalkerAlwaysStepsAroundAnAwayNeighbourAfterTheShortJam() {
		assertTrue(HuntApproach.yieldNow(30, true, false, true, 6.5, CLOSEST), "an away hunter never gives way, whatever the ids");
		assertFalse(HuntApproach.yieldNow(29, true, false, true, 6.5, CLOSEST), "after the short push, not before");
		assertFalse(HuntApproach.yieldNow(30, true, false, true, 6.0, CLOSEST), "within the arrival distance it goes out beside them instead");
		assertFalse(HuntApproach.yieldNow(30, false, false, true, 6.5, CLOSEST), "nobody touching");
	}
}
