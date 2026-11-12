package dev.villageheart.core.growth;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-5 (M23 plan 5.1). */
class LicenseTest {
	static final BreedingPair PAIR = BreedingPair.of(new UUID(0, 1), new UUID(0, 2));
	static final UUID CHILD = new UUID(5, 5);

	static License fresh() { return License.issue(4, PAIR, OptionalLong.of(77)); }

	@Test void legalPath() {
		License l = fresh();
		assertEquals(LicenceState.WAITING, l.state());
		l = l.approaching();
		assertEquals(LicenceState.APPROACHING, l.state());
		l = l.seeded(77, 12, 3);
		assertEquals(LicenceState.SEEDED, l.state());
		assertEquals(1, l.seeds());
		l = l.born(CHILD);
		assertEquals(LicenceState.BORN, l.state());
		assertEquals(CHILD, l.child().orElseThrow());
		assertFalse(l.active());
	}

	@Test void abortGoesBackToApproachingAndKeepsSeeds() {
		License l = fresh().seeded(77, 5, 6).aborted();
		assertEquals(LicenceState.APPROACHING, l.state());
		assertEquals(1, l.seeds());
		assertEquals(1, l.aborts());
		assertEquals(OptionalLong.of(77), l.bed(), "a bed nobody took is still held");
	}

	@Test void priorFoodIsSetByTheFirstSeedOnly() {
		License l = fresh().seeded(77, 5, 6).aborted().seeded(77, 12, 12);
		assertEquals(5, l.priorFoodA());
		assertEquals(6, l.priorFoodB());
		assertEquals(2, l.seeds());
	}

	@Test void mayReseedStopsAtMaxSeeds() {
		License l = fresh();
		for (int i = 0; i < Balance.COURTSHIP_MAX_SEEDS; i++) {
			assertTrue(l.mayReseed(), "seed " + i);
			l = l.seeded(77, 0, 0);
			if (i < Balance.COURTSHIP_MAX_SEEDS - 1) l = l.aborted();
		}
		l = l.aborted();
		assertFalse(l.mayReseed());
		assertThrows(IllegalStateException.class, () -> fresh().seeded(1, 0, 0).aborted().seeded(1, 0, 0).aborted().seeded(1, 0, 0).aborted().seeded(1, 0, 0));
	}

	@Test void illegalJumpsThrow() {
		assertThrows(IllegalStateException.class, () -> fresh().born(CHILD));
		assertThrows(IllegalStateException.class, () -> fresh().approaching().born(CHILD));
		assertThrows(IllegalStateException.class, () -> fresh().aborted());
		assertThrows(IllegalStateException.class, () -> fresh().handOver());
		License born = fresh().seeded(77, 0, 0).born(CHILD);
		assertThrows(IllegalStateException.class, () -> born.lapsed(LapseReason.NEXT_DAWN));
		assertThrows(IllegalStateException.class, born::approaching);
		assertThrows(IllegalStateException.class, () -> born.seeded(1, 0, 0));
		License lapsed = fresh().lapsed(LapseReason.CANNOT_MEET);
		assertThrows(IllegalStateException.class, () -> lapsed.lapsed(LapseReason.NEXT_DAWN));
		assertThrows(IllegalStateException.class, () -> lapsed.seeded(1, 0, 0));
		assertThrows(IllegalStateException.class, lapsed::tickApproach);
	}

	@Test void holdsBedFromTheDawnUntilHandOver() {
		License l = fresh();
		assertTrue(l.holdsBed());
		l = l.seeded(77, 0, 0);
		assertTrue(l.holdsBed());
		License h = l.handOver();
		assertFalse(h.holdsBed(), "after the hand-over the ticket is vanilla's");
		assertTrue(h.lapsed(LapseReason.PARENT_GONE).bed().isPresent());
		assertFalse(h.lapsed(LapseReason.PARENT_GONE).holdsBed(), "a lapse after hand-over asks for no release");
		assertTrue(l.lapsed(LapseReason.RELEASED).lapse().isPresent());
		assertFalse(License.issue(4, PAIR, OptionalLong.empty()).holdsBed());
	}

	@Test void abortAfterHandOverLosesTheBed() {
		License l = fresh().seeded(77, 0, 0).handOver().aborted();
		assertTrue(l.bed().isEmpty());
		assertFalse(l.handedOver());
	}

	@Test void lapseKeepsItsReason() {
		for (LapseReason r : LapseReason.values()) assertEquals(r, fresh().lapsed(r).lapse().orElseThrow());
		assertEquals(LicenceState.LAPSED, fresh().lapsed(LapseReason.NEXT_DAWN).state());
	}

	@Test void staleDay() {
		assertTrue(fresh().staleOn(5));
		assertFalse(fresh().staleOn(4));
	}

	@Test void recordRejectsInconsistentStates() {
		assertThrows(IllegalArgumentException.class, () -> new License(1, PAIR, LicenceState.BORN, 1, 0, OptionalLong.empty(), false, 0, 0, 0,
				java.util.Optional.empty(), java.util.Optional.empty()));
		assertThrows(IllegalArgumentException.class, () -> new License(1, PAIR, LicenceState.LAPSED, 1, 0, OptionalLong.empty(), false, 0, 0, 0,
				java.util.Optional.empty(), java.util.Optional.empty()));
	}
}
