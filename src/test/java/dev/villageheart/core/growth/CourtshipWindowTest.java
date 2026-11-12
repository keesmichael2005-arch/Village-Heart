package dev.villageheart.core.growth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-4 (M23 plan 5.1). */
class CourtshipWindowTest {
	static final Kind W = new Kind(Role.WORKER, true);
	static final Kind U = new Kind(Role.UNEMPLOYED, true);
	static final Kind UN = new Kind(Role.UNEMPLOYED, false);
	static final Kind P = new Kind(Role.PROFESSIONAL, true);
	static final Kind PN = new Kind(Role.PROFESSIONAL, false);

	static void yes(int phase, Kind k) { assertTrue(CourtshipWindow.canSeed(phase, k, k), "phase " + phase + " " + k); }

	static void no(int phase, Kind k) { assertFalse(CourtshipWindow.canSeed(phase, k, k), "phase " + phase + " " + k); }

	@Test void workers() {
		yes(0, W); yes(599, W); no(600, W); no(1000, W);
	}

	@Test void unemployedWithMeetingPoint() {
		no(9, U); yes(10, U); yes(8599, U); no(8600, U); no(10999, U);
		yes(11000, U); yes(11599, U); no(11600, U);
	}

	@Test void unemployedWithoutMeetingPoint() {
		yes(8600, UN); yes(11599, UN); no(11600, UN); no(9, UN);
	}

	@Test void professionals() {
		yes(1599, P); no(1600, P); no(9500, P); yes(11000, P);
		yes(9000, PN); yes(1599, PN); no(1600, PN); no(8599, PN);
	}

	@Test void mixedKindsUseTheIntersection() {
		assertFalse(CourtshipWindow.canSeed(5, W, U));
		assertTrue(CourtshipWindow.canSeed(10, W, U));
		assertTrue(CourtshipWindow.canSeed(599, W, U));
		assertFalse(CourtshipWindow.canSeed(600, W, U));
		// both IDLE at 1200 would need different windows: a worker is already off
		assertFalse(CourtshipWindow.canSeed(11000, W, U));
	}

	@Test void stillSeedableToday() {
		assertTrue(CourtshipWindow.canStillSeedToday(0, W, U));
		assertFalse(CourtshipWindow.canStillSeedToday(600, W, U));
		assertTrue(CourtshipWindow.canStillSeedToday(600, U, U));
		assertFalse(CourtshipWindow.canStillSeedToday(11600, U, U));
	}
}
