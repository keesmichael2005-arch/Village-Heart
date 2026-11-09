package dev.villageheart.core.tier;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import dev.villageheart.core.Balance;

class TierProgressTest {
	private static TierProgress fed(TierProgress p, long day) { return p.afterDinner(day, 6, 6); }

	@Test void initial() {
		assertEquals(0, TierProgress.INITIAL.fedStreak());
		assertEquals(TierProgress.NO_DAY, TierProgress.INITIAL.lastDinnerDay());
		assertEquals(0, TierProgress.INITIAL.bestDawnTreasury());
	}

	@Test void fedDaysCountAndAHungryDayResets() {
		TierProgress p = fed(fed(fed(TierProgress.INITIAL, 0), 1), 2);
		assertEquals(3, p.fedStreak());
		p = p.afterDinner(3, 6, 5);
		assertEquals(0, p.fedStreak());
		assertEquals(3, p.lastDinnerDay());
		assertEquals(1, fed(p, 4).fedStreak());
	}

	@Test void aDinnerWithNobodyResets() {
		assertEquals(0, fed(TierProgress.INITIAL, 0).afterDinner(1, 0, 0).fedStreak());
	}

	@Test void theSameDayCountsOnce() {
		TierProgress p = fed(TierProgress.INITIAL, 4);
		assertSame(p, fed(p, 4));
		assertEquals(1, fed(p, 4).fedStreak());
	}

	@Test void timeSetBackStillCounts() {
		assertEquals(2, fed(fed(TierProgress.INITIAL, 5), 0).fedStreak());
	}

	@Test void skippedDaysDoNotReset() {
		assertEquals(2, fed(fed(TierProgress.INITIAL, 3), 7).fedStreak());
	}

	@Test void saturates() {
		TierProgress p = new TierProgress(Balance.MAX_FED_STREAK, 1, 0);
		assertEquals(Balance.MAX_FED_STREAK, fed(p, 2).fedStreak());
	}

	@Test void pingPongCountsEachTime() {
		TierProgress p = TierProgress.INITIAL;
		for (long d : new long[] {5, 6, 5, 6}) p = fed(p, d);
		assertEquals(4, p.fedStreak());
	}

	@Test void dawnBestNeverLowers() {
		TierProgress p = TierProgress.INITIAL.atDawn(40);
		assertEquals(40, p.atDawn(10).bestDawnTreasury());
		assertEquals(50, p.atDawn(50).bestDawnTreasury());
	}

	@Test void negativesRejected() {
		assertThrows(IllegalArgumentException.class, () -> new TierProgress(-1, 0, 0));
		assertThrows(IllegalArgumentException.class, () -> new TierProgress(0, 0, -1));
		assertThrows(IllegalArgumentException.class, () -> TierProgress.INITIAL.atDawn(-1));
		assertThrows(IllegalArgumentException.class, () -> TierProgress.INITIAL.afterDinner(0, -1, 0));
	}
}
