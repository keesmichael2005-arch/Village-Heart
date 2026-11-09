package dev.villageheart.core.tier;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import dev.villageheart.core.Tier;

class TierDawnTest {
	private static final TierInputs VILLAGE_OK = new TierInputs(6, 3, 0, true, true, true);
	private static final TierInputs EVERYTHING = new TierInputs(12, 8, 2, true, true, true);

	private static TierProgress fed(TierProgress p, long day) { return p.afterDinner(day, 6, 6); }

	@Test void threeFedDaysThenDawnTiersUp() {
		TierProgress p = fed(fed(fed(TierProgress.INITIAL, 0), 1), 2);
		DawnOutcome o = TierDawn.run(Tier.HAMLET, p, VILLAGE_OK, 0);
		assertTrue(o.tieredUp());
		assertEquals(Tier.VILLAGE, o.tier());
	}

	@Test void aHungryDayDelaysItToTheDawnAfterTheSixthDinner() {
		TierProgress p = TierProgress.INITIAL;
		p = fed(p, 0);
		p = fed(p, 1);
		p = p.afterDinner(2, 6, 4);
		p = fed(p, 3);
		p = fed(p, 4);
		assertFalse(TierDawn.run(Tier.HAMLET, p, VILLAGE_OK, 0).tieredUp());
		p = fed(p, 5);
		assertTrue(TierDawn.run(Tier.HAMLET, p, VILLAGE_OK, 0).tieredUp());
	}

	@Test void oneStepPerDawn() {
		TierProgress p = new TierProgress(7, 0, 0);
		DawnOutcome o = TierDawn.run(Tier.HAMLET, p, EVERYTHING, 40);
		assertEquals(Tier.VILLAGE, o.tier());
		DawnOutcome o2 = TierDawn.run(o.tier(), o.progress(), EVERYTHING, 40);
		assertEquals(Tier.TOWN, o2.tier());
	}

	@Test void neverDown() {
		DawnOutcome o = TierDawn.run(Tier.VILLAGE, TierProgress.INITIAL, TierInputs.EMPTY, 0);
		assertEquals(Tier.VILLAGE, o.tier());
		assertFalse(o.tieredUp());
	}

	@Test void townIsUnchanged() {
		DawnOutcome o = TierDawn.run(Tier.TOWN, new TierProgress(9, 0, 99), EVERYTHING, 99);
		assertEquals(Tier.TOWN, o.tier());
		assertFalse(o.tieredUp());
	}

	@Test void treasuryIsRecordedBeforeEvaluating() {
		TierProgress p = new TierProgress(7, 0, 0);
		assertTrue(TierDawn.run(Tier.VILLAGE, p, EVERYTHING, 32).tieredUp());
		assertFalse(TierDawn.run(Tier.VILLAGE, p, EVERYTHING, 31).tieredUp());
	}

	@Test void reportReflectsTheNewProgress() {
		DawnOutcome o = TierDawn.run(Tier.VILLAGE, new TierProgress(7, 0, 0), EVERYTHING, 20);
		Goal t = o.report().goals().stream().filter(g -> g.id() == GoalId.DAWN_TREASURY).findFirst().orElseThrow();
		assertEquals(20, t.have());
		assertEquals(20, o.progress().bestDawnTreasury());
	}
}
