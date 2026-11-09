package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.JobLevels;
import dev.villageheart.core.tier.TierRules;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LevelingTest {
	private static final Progress FRESH = new Progress(1, 0);

	@Test
	void thresholdsAtTown() {
		XpChange c = Leveling.gain(FRESH, 99, Tier.TOWN);
		assertEquals(new Progress(1, 99), c.after());
		assertFalse(c.levelledUp());
		c = Leveling.gain(c.after(), 1, Tier.TOWN);
		assertEquals(new Progress(2, 100), c.after());
		assertTrue(c.levelledUp());
		assertFalse(c.reachedCap());
		c = Leveling.gain(c.after(), 199, Tier.TOWN);
		assertEquals(new Progress(2, 299), c.after());
		assertFalse(c.levelledUp());
		c = Leveling.gain(c.after(), 1, Tier.TOWN);
		assertEquals(new Progress(3, 300), c.after());
		assertTrue(c.levelledUp());
	}

	@Test
	void aBigGainSkipsALevelAndClamps() {
		XpChange c = Leveling.gain(FRESH, 1000, Tier.TOWN);
		assertEquals(new Progress(3, 300), c.after());
		assertTrue(c.levelledUp());
		assertEquals(new Progress(3, 300), Leveling.gain(c.after(), 5, Tier.TOWN).after());
		assertFalse(Leveling.gain(c.after(), 5, Tier.TOWN).levelledUp());
		assertEquals(300, Leveling.gain(new Progress(1, 5), Integer.MAX_VALUE, Tier.HAMLET).after().xp());
	}

	@Test
	void negativeThrowsAndZeroChangesNothing() {
		assertThrows(IllegalArgumentException.class, () -> Leveling.gain(FRESH, -1, Tier.TOWN));
		XpChange c = Leveling.gain(new Progress(2, 150), 0, Tier.TOWN);
		assertEquals(new Progress(2, 150), c.after());
		assertFalse(c.levelledUp());
		assertFalse(c.reachedCap());
	}

	@Test
	void thresholdsAreTheDisplayThresholds() {
		assertEquals(JobLevels.xpForNext(1), 100);
		assertEquals(JobLevels.xpForNext(2), 300);
		assertEquals(1, TierRules.levelFromXp(JobLevels.xpForNext(1) - 1));
		assertEquals(2, TierRules.levelFromXp(JobLevels.xpForNext(1)));
		assertEquals(2, TierRules.levelFromXp(JobLevels.xpForNext(2) - 1));
		assertEquals(3, TierRules.levelFromXp(JobLevels.xpForNext(2)));
		assertEquals(3, TierRules.levelFromXp(1_000_000));
		assertEquals(1, TierRules.levelFromXp(0));
	}

	@Test
	void tierCaps() {
		assertEquals(1, TierRules.levelCap(Tier.HAMLET));
		assertEquals(2, TierRules.levelCap(Tier.VILLAGE));
		assertEquals(3, TierRules.levelCap(Tier.TOWN));
		assertEquals(1, TierRules.levelFor(300, Tier.HAMLET));
		assertEquals(2, TierRules.levelFor(300, Tier.VILLAGE));
		assertEquals(3, TierRules.levelFor(300, Tier.TOWN));
		assertEquals(1, TierRules.levelFor(99, Tier.TOWN));
	}

	@Test
	void hamletCapHoldsAndBanksXp() {
		XpChange c = Leveling.gain(FRESH, 300, Tier.HAMLET);
		assertEquals(new Progress(1, 300), c.after());
		assertFalse(c.levelledUp());
		assertTrue(c.reachedCap(), "the gain that crossed 100");
		// only the crossing gain reports it
		assertFalse(Leveling.gain(c.after(), 10, Tier.HAMLET).reachedCap());
		XpChange a = Leveling.gain(FRESH, 99, Tier.HAMLET);
		assertFalse(a.reachedCap());
		XpChange b = Leveling.gain(a.after(), 1, Tier.HAMLET);
		assertTrue(b.reachedCap());
		assertFalse(b.levelledUp());
		assertFalse(Leveling.gain(b.after(), 1, Tier.HAMLET).reachedCap());
	}

	@Test
	void villageCapsAtTwo() {
		XpChange c = Leveling.gain(FRESH, 300, Tier.VILLAGE);
		assertEquals(new Progress(2, 300), c.after());
		assertTrue(c.levelledUp());
		assertTrue(c.reachedCap(), "300 crossed while the Village holds level 2");
		XpChange d = Leveling.gain(new Progress(2, 299), 1, Tier.VILLAGE);
		assertTrue(d.reachedCap());
		assertFalse(d.levelledUp());
		// crossing 100 at Village is a level-up, not a capped notice
		XpChange e = Leveling.gain(new Progress(1, 99), 1, Tier.VILLAGE);
		assertTrue(e.levelledUp());
		assertFalse(e.reachedCap());
		assertTrue(Leveling.capped(new Progress(2, 300), Tier.VILLAGE));
	}

	@Test
	void cappedAndNeededTier() {
		assertTrue(Leveling.capped(new Progress(1, 150), Tier.HAMLET));
		assertFalse(Leveling.capped(new Progress(1, 50), Tier.HAMLET));
		assertFalse(Leveling.capped(new Progress(3, 300), Tier.TOWN));
		assertFalse(Leveling.capped(new Progress(1, 150), Tier.VILLAGE));
		assertFalse(Leveling.capped(new Progress(2, 150), Tier.VILLAGE));
		assertEquals(Optional.of(Tier.VILLAGE), Leveling.tierForNext(new Progress(1, 0)));
		assertEquals(Optional.of(Tier.TOWN), Leveling.tierForNext(new Progress(2, 0)));
		assertEquals(Optional.empty(), Leveling.tierForNext(new Progress(3, 0)));
	}

	@Test
	void levelNowRaisesAndNeverLowers() {
		assertEquals(2, Leveling.levelNow(new Progress(1, 300), Tier.VILLAGE));
		assertEquals(3, Leveling.levelNow(new Progress(1, 300), Tier.TOWN));
		assertEquals(1, Leveling.levelNow(new Progress(1, 300), Tier.HAMLET));
		assertEquals(3, Leveling.levelNow(new Progress(3, 0), Tier.HAMLET), "a level above the cap is kept");
		assertEquals(3, Leveling.gain(new Progress(3, 0), 5, Tier.HAMLET).after().level());
	}

	@Test
	void sanitizeClamps() {
		assertEquals(new Progress(1, 0), Leveling.sanitize(0, -5));
		assertEquals(new Progress(3, 300), Leveling.sanitize(7, 10_000));
		assertEquals(new Progress(2, 150), Leveling.sanitize(2, 150));
		assertThrows(IllegalArgumentException.class, () -> new Progress(0, 0));
		assertThrows(IllegalArgumentException.class, () -> new Progress(1, -1));
	}
}
