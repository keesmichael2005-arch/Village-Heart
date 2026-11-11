package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.raid.RaidOrigin;
import org.junit.jupiter.api.Test;

/** T-L1-26. A Bad Omen victory pays nothing, and a second random victory inside five days pays nothing. */
class RewardTest {
	@Test
	void badOmenPaysNothingAndTheGapPaysOnce() {
		assertEquals(0, ThreatRules.reward(RaidOrigin.BAD_OMEN, 5, 5, 30, 10, ThreatState.NO_DAY));
		assertEquals(0, ThreatRules.reward(RaidOrigin.TEST, 5, 1, 2, 10, ThreatState.NO_DAY));
		assertEquals(16, ThreatRules.reward(RaidOrigin.RANDOM, 5, 1, 2, 10, ThreatState.NO_DAY));
		assertEquals(0, ThreatRules.reward(RaidOrigin.RANDOM, 5, 1, 2, 14, 10));
		assertEquals(16, ThreatRules.reward(RaidOrigin.RANDOM, 5, 1, 2, 15, 10));
	}
}
