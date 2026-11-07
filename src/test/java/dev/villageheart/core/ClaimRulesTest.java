package dev.villageheart.core;

import org.junit.jupiter.api.Test;

import static dev.villageheart.core.ClaimRules.Result.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimRulesTest {
	@Test void claimsWhenBellIsClose() { assertEquals(CLAIMED, ClaimRules.evaluate(3, false)); }
	@Test void refusesWithoutBell() { assertEquals(NO_BELL_NEARBY, ClaimRules.evaluate(-1, false)); }
	@Test void refusesWhenBellTooFar() { assertEquals(NO_BELL_NEARBY, ClaimRules.evaluate(40, false)); }
	@Test void onlyOneVillagePerWorld() { assertEquals(ALREADY_HAVE_VILLAGE, ClaimRules.evaluate(3, true)); }
	@Test void alreadyClaimedBeatsMissingBell() { assertEquals(ALREADY_HAVE_VILLAGE, ClaimRules.evaluate(-1, true)); }
	@Test void boundaryDistance() {
		assertEquals(CLAIMED, ClaimRules.evaluate(ClaimRules.MAX_BELL_DISTANCE, false));
		assertEquals(NO_BELL_NEARBY, ClaimRules.evaluate(ClaimRules.MAX_BELL_DISTANCE + 0.01, false));
	}
}
