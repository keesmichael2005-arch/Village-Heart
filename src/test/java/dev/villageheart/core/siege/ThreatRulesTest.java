package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.siege.ThreatRules.Gates;
import org.junit.jupiter.api.Test;

/** T-L1-20. A raid rolls only for a rested village, and it starts only while the player has been close. */
class ThreatRulesTest {
	@Test
	void rollIsBlockedByTheListedConditions() {
		assertTrue(ThreatRules.mayRollRaid(Tier.VILLAGE, 7, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollRaid(Tier.HAMLET, 20, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 6, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, 4, false, false, Gates.OPEN));
		assertTrue(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, 5, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, ThreatState.NO_DAY, true, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, ThreatState.NO_DAY, false, true, Gates.OPEN));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, ThreatState.NO_DAY, false, false, new Gates(true, true, true, true, true)));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, ThreatState.NO_DAY, false, false, new Gates(false, false, true, true, true)));
		assertFalse(ThreatRules.mayRollRaid(Tier.VILLAGE, 20, ThreatState.NO_DAY, false, false, new Gates(false, true, false, true, true)));
	}

	@Test
	void loadedStreakAndTheStartWindow() {
		int streak = 0;
		for (int i = 0; i < Balance.RAID_LOADED_TICKS - 1; i++) streak = ThreatRules.loaded(streak, true);
		assertFalse(ThreatRules.loadedEnough(streak));
		streak = ThreatRules.loaded(streak, true);
		assertTrue(ThreatRules.loadedEnough(streak));
		assertEquals(0, ThreatRules.loaded(streak, false));

		int start = 5000;
		assertTrue(ThreatRules.mayStart(start, start, 600, false, ThreatState.NO_DAY));
		assertTrue(ThreatRules.mayStart(start, start, 600, false, 2));
		assertFalse(ThreatRules.mayStart(start - 1, start, 600, false, 2));
		assertFalse(ThreatRules.mayStart(Balance.PENDING_START_MAX + 1, start, 600, false, 2));
		assertTrue(ThreatRules.mayStart(Balance.PENDING_START_MAX, start, 600, false, 2));
		assertFalse(ThreatRules.mayStart(start, start, 599, false, 2));
		assertFalse(ThreatRules.mayStart(start, start, 600, true, 2));
		assertEquals(16, ThreatRules.reward(RaidOrigin.RANDOM, 5, 1, 2, 10, ThreatState.NO_DAY));
		assertEquals(40, ThreatRules.reward(RaidOrigin.RANDOM, 8, 5, 30, 10, ThreatState.NO_DAY));
	}
}
