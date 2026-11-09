package dev.villageheart.core.tier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.JobLevels;

class TierRulesTest {
	@Test void nextWalksTheTiers() {
		assertEquals(Optional.of(Tier.VILLAGE), TierRules.next(Tier.HAMLET));
		assertEquals(Optional.of(Tier.TOWN), TierRules.next(Tier.VILLAGE));
		assertEquals(Optional.empty(), TierRules.next(Tier.TOWN));
	}

	@Test void caps() {
		assertEquals(1, TierRules.levelCap(Tier.HAMLET));
		assertEquals(2, TierRules.levelCap(Tier.VILLAGE));
		assertEquals(3, TierRules.levelCap(Tier.TOWN));
	}

	@Test void levelFromXpIsCumulativeAndMatchesTheBar() {
		assertEquals(1, TierRules.levelFromXp(JobLevels.xpForNext(1) - 1));
		assertEquals(2, TierRules.levelFromXp(JobLevels.xpForNext(1)));
		assertEquals(3, TierRules.levelFromXp(JobLevels.xpForNext(2)));
		assertEquals(1, TierRules.levelFromXp(99));
		assertEquals(2, TierRules.levelFromXp(100));
		assertEquals(3, TierRules.levelFromXp(300));
	}

	@Test void levelForHoldsDownByTier() {
		assertEquals(1, TierRules.levelFor(300, Tier.HAMLET));
		assertEquals(2, TierRules.levelFor(300, Tier.VILLAGE));
		assertEquals(3, TierRules.levelFor(300, Tier.TOWN));
		assertEquals(1, TierRules.levelFor(0, Tier.TOWN));
	}
}
