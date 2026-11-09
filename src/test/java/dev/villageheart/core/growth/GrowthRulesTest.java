package dev.villageheart.core.growth;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-1..T-L1-3 (M23 plan 5.1). */
class GrowthRulesTest {
	static final Kind K = new Kind(Role.UNEMPLOYED, true);

	static UUID id(int n) { return new UUID(0, n); }

	static Candidate c(int n, double x, boolean ready) { return new Candidate(id(n), x, 0, 0, ready, K); }

	static Decision decide(int beds, double days, List<Candidate> census) {
		return GrowthRules.decide(beds, OptionalDouble.of(days), census, Set.of());
	}

	@Test void twoReadyOneBedThreeDaysLicenses() {
		Decision d = decide(1, 3.0, List.of(c(1, 0, true), c(2, 1, true)));
		assertEquals(Reason.LICENSED, d.reason());
		assertEquals(BreedingPair.of(id(1), id(2)), d.pair().orElseThrow());
	}

	@Test void noFreeBed() {
		assertEquals(Reason.NO_FREE_BED, decide(0, 9, List.of(c(1, 0, true), c(2, 1, true))).reason());
	}

	@Test void foodThresholdIsInclusiveAtThree() {
		List<Candidate> two = List.of(c(1, 0, true), c(2, 1, true));
		assertEquals(Reason.LOW_FOOD, decide(1, 2.99, two).reason());
		assertEquals(Reason.LICENSED, decide(1, 3.0, two).reason());
		assertEquals(Reason.LOW_FOOD, GrowthRules.decide(1, OptionalDouble.empty(), two, Set.of()).reason());
	}

	@Test void nobodyIsCheckedFirst() {
		assertEquals(Reason.NOBODY, GrowthRules.decide(0, OptionalDouble.empty(), List.of(), Set.of()).reason());
		assertEquals(Reason.NOBODY, decide(1, 9, List.of()).reason());
	}

	@Test void tooFewReady() {
		assertEquals(Reason.TOO_FEW_READY, decide(1, 9, List.of(c(1, 0, true), c(2, 1, false), c(3, 2, false), c(4, 3, false))).reason());
	}

	@Test void checkOrder() {
		List<Candidate> one = List.of(c(1, 0, true), c(2, 1, false));
		assertEquals(Reason.NO_FREE_BED, decide(0, 1.0, one).reason());
		assertEquals(Reason.LOW_FOOD, decide(1, 1.0, one).reason());
		assertEquals(Reason.TOO_FEW_READY, decide(1, 5.0, one).reason());
	}

	@Test void readyMapping() {
		assertTrue(GrowthRules.ready(true, true, 0));
		assertFalse(GrowthRules.ready(false, true, 0));
		assertFalse(GrowthRules.ready(true, false, 0));
		assertFalse(GrowthRules.ready(true, true, 6000));
		assertFalse(GrowthRules.ready(true, true, -24000));
	}

	@Test void closestPairWinsAndOrderDoesNotMatter() {
		List<Candidate> census = new ArrayList<>(List.of(c(1, 0, true), c(2, 10, true), c(3, 11, true), c(4, 30, true)));
		BreedingPair want = BreedingPair.of(id(2), id(3));
		Random r = new Random(7);
		for (int i = 0; i < 20; i++) {
			Collections.shuffle(census, r);
			assertEquals(want, decide(1, 9, census).pair().orElseThrow());
		}
	}

	@Test void equalDistanceTakesTheLowerPair() {
		List<Candidate> census = new ArrayList<>(List.of(c(5, 0, true), c(6, 1, true), c(1, 100, true), c(2, 101, true)));
		for (int i = 0; i < 10; i++) {
			Collections.shuffle(census, new Random(i));
			assertEquals(BreedingPair.of(id(1), id(2)), decide(1, 9, census).pair().orElseThrow());
		}
	}

	@Test void skippedPairsAreAvoidedWhileAnotherExists() {
		List<Candidate> census = List.of(c(1, 0, true), c(2, 1, true), c(3, 5, true));
		BreedingPair p12 = BreedingPair.of(id(1), id(2)), p23 = BreedingPair.of(id(2), id(3)), p13 = BreedingPair.of(id(1), id(3));
		assertEquals(p23, GrowthRules.decide(1, OptionalDouble.of(9), census, Set.of(p12)).pair().orElseThrow());
		assertEquals(p13, GrowthRules.decide(1, OptionalDouble.of(9), census, Set.of(p12, p23)).pair().orElseThrow());
		assertEquals(p12, GrowthRules.decide(1, OptionalDouble.of(9), census, Set.of(p12, p23, p13)).pair().orElseThrow());
	}

	@Test void pairIsOrdered() {
		BreedingPair p = BreedingPair.of(id(9), id(2));
		assertTrue(p.a().compareTo(p.b()) < 0);
		assertThrows(IllegalArgumentException.class, () -> new BreedingPair(id(9), id(2)));
		assertThrows(IllegalArgumentException.class, () -> new BreedingPair(id(2), id(2)));
	}

	@Test void nearestBed() {
		List<long[]> beds = List.of(new long[] {30, 10, 0, 0}, new long[] {20, 3, 0, 0}, new long[] {10, 7, 0, 0});
		assertEquals(Optional.of(1), GrowthRules.nearestBed(2, 0, 0, beds));
		assertEquals(Optional.empty(), GrowthRules.nearestBed(0, 0, 0, List.of()));
		// tie: the lower packed position
		List<long[]> tie = List.of(new long[] {50, 4, 0, 0}, new long[] {40, -4, 0, 0});
		assertEquals(Optional.of(1), GrowthRules.nearestBed(0, 0, 0, tie));
	}

	@Test void failedPairsExpireAfterThreeDays() {
		BreedingPair p = BreedingPair.of(id(1), id(2));
		GrowthState s = GrowthState.EMPTY.withFailed(p, 10);
		assertEquals(Set.of(p), s.pruned(13).skipPairs());
		assertEquals(Set.of(), s.pruned(14).skipPairs());
		// failing again refreshes the entry instead of duplicating it
		assertEquals(1, s.withFailed(p, 12).failedPairs().size());
	}
}
