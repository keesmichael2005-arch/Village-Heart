package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.siege.ThreatRules;
import dev.villageheart.core.siege.ThreatState;
import org.junit.jupiter.api.Test;

/** T-L1-27. A young village gets a short raid. Four guards, or a town, get the full one. */
class RaidPlanTest {
	@Test
	void youngVillageIsCappedAndAFullVillageIsNot() {
		assertEarly(RaidPlans.of(Tier.VILLAGE, 2, 8, RaidOrigin.RANDOM), 2);
		assertEarly(RaidPlans.of(Tier.VILLAGE, 3, 8, RaidOrigin.RANDOM), 2);
		assertEarly(RaidPlans.of(Tier.VILLAGE, 1, 8, RaidOrigin.RANDOM), 1);
		assertEarly(RaidPlans.of(Tier.VILLAGE, 0, 8, RaidOrigin.RANDOM), 1);

		RaidPlan full = RaidPlans.of(Tier.VILLAGE, 4, 8, RaidOrigin.RANDOM);
		assertEquals(0, full.waveCap());
		assertFalse(full.early());
		assertEquals(3, full.omen());
		assertEquals(4, full.extraVindicators());
		assertEquals(2, full.extraRavagers());

		RaidPlan town = RaidPlans.of(Tier.TOWN, 2, 8, RaidOrigin.RANDOM);
		assertEquals(0, town.waveCap());
		assertEquals(3, town.omen());

		for (RaidOrigin origin : new RaidOrigin[] {RaidOrigin.BAD_OMEN, RaidOrigin.TEST}) {
			RaidPlan plain = RaidPlans.of(Tier.VILLAGE, 0, 8, origin);
			assertEquals(0, plain.waveCap());
			assertFalse(plain.early());
			assertEquals(0, plain.extraVindicators());
			assertEquals(0, plain.extraRavagers());
		}

		assertEquals(8, ThreatRules.reward(RaidOrigin.RANDOM, 2, 1, 0, 3, ThreatState.NO_DAY));
		assertEquals(6, ThreatRules.reward(RaidOrigin.RANDOM, 1, 1, 0, 3, ThreatState.NO_DAY));
		assertFalse(ThreatRules.mayStart(5000, 4000, 600, false, 1));
		assertTrue(ThreatRules.mayStart(5000, 4000, 600, false, 2));
	}

	private static void assertEarly(RaidPlan plan, int waves) {
		assertEquals(waves, plan.waveCap());
		assertEquals(1, plan.omen());
		assertEquals(0, plan.extraVindicators());
		assertEquals(0, plan.extraRavagers());
		assertTrue(plan.early());
	}
}
