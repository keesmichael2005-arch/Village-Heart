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
	void touchingNearTheMarkerArrivesEvenWhenTheWalkGivesUp() {
		assertEquals(Choice.ARRIVE, HuntApproach.choose(6.0, CLOSEST, true, true));
		assertEquals(Choice.ARRIVE, HuntApproach.choose(3.0, CLOSEST, true, false));
	}

	@Test
	void aJamFartherOutYieldsInsteadOfTheLongRetry() {
		assertEquals(Choice.YIELD, HuntApproach.choose(12.0, CLOSEST, true, true));
		assertEquals(Choice.UNREACHABLE, HuntApproach.choose(12.0, CLOSEST, false, true));
		assertEquals(Choice.WALK, HuntApproach.choose(12.0, CLOSEST, false, false));
	}

	@Test
	void theHigherIdYieldsAfterAShortJamAndNotWhenItCanAlreadyGoOut() {
		assertFalse(HuntApproach.yieldNow(29, true, true, 12.0, CLOSEST));
		assertTrue(HuntApproach.yieldNow(30, true, true, 12.0, CLOSEST));
		assertFalse(HuntApproach.yieldNow(30, true, false, 12.0, CLOSEST), "the lower id keeps the path");
		assertFalse(HuntApproach.yieldNow(30, false, true, 12.0, CLOSEST));
		assertFalse(HuntApproach.yieldNow(30, true, true, 6.0, CLOSEST), "close enough to go out beside them");
	}
}
