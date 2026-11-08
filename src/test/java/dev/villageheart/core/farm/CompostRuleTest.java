package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompostRuleTest {
	/** Feeds {@code seeds} seeds into a composter; a full one (7) turns READY and is emptied at once (one bone meal). Returns {level, tenths, boneMeal}. */
	private static int[] feed(int level, int tenths, int seeds) {
		int bone = 0;
		for (int i = 0; i < seeds; i++) {
			CompostRule.Result r = CompostRule.insert(level, tenths);
			if (!r.accepted()) {
				level = 0; // READY: the farmer takes the bone meal
				tenths = 0;
				bone++;
				r = CompostRule.insert(level, tenths);
			}
			level = r.level();
			tenths = r.tenths();
		}
		return new int[] {level, tenths, bone};
	}

	@Test
	void theFirstSeedAlwaysRaisesTheLevel() {
		CompostRule.Result r = CompostRule.insert(0, 0);
		assertTrue(r.accepted());
		assertEquals(1, r.level());
		assertEquals(0, r.tenths());
	}

	@Test
	void twentyOneSeedsFillTheComposter() {
		assertEquals(21, CompostRule.SEEDS_PER_BONE_MEAL);
		int[] s = feed(0, 0, 21);
		assertEquals(7, s[0], "level 7 after exactly 21 seeds");
		assertEquals(0, s[2]);
		int[] twenty = feed(0, 0, 20);
		assertTrue(twenty[0] < 7, "20 seeds are not enough: " + twenty[0]);
	}

	@Test
	void aFullOrReadyComposterRefuses() {
		for (int level : new int[] {7, 8}) {
			CompostRule.Result r = CompostRule.insert(level, 4);
			assertFalse(r.accepted());
			assertEquals(level, r.level());
			assertEquals(4, r.tenths());
		}
	}

	@Test
	void twentyFiveSeedsGiveOneBoneMealLevelOneTenthsNine() {
		// 21 seeds -> level 7 (bone meal taken), seed 22 -> level 1 (0 tenths), seeds 23-25: 3 x 3 tenths = 9
		int[] s = feed(0, 0, 25);
		assertEquals(1, s[2]);
		assertEquals(1, s[0]);
		assertEquals(9, s[1]);
	}

	@Test
	void fortyOneSeedsGiveOneBoneMealLevelSixTenthsSeven() {
		// 21 seeds -> bone meal; seed 22 -> level 1; 19 more seeds = 57 tenths = 5 levels + 7
		int[] s = feed(0, 0, 41);
		assertEquals(1, s[2]);
		assertEquals(6, s[0]);
		assertEquals(7, s[1]);
	}

	@Test
	void theMeanMatchesVanilla() {
		// vanilla: the first seed always counts, then 6 successes at chance 0.3 -> 1 + 6 / 0.3 = 21
		assertEquals(1 + 6 / 0.3, CompostRule.SEEDS_PER_BONE_MEAL, 1e-9);
		int[] s = feed(0, 0, 21 * 10);
		assertEquals(9, s[2], "ten full cycles: nine bone meals taken, the tenth composter is full (7)");
		assertEquals(7, s[0]);
	}

	@Test
	void thirtyFourSeedsLikeTheKeepTest() {
		// 34 surplus seeds: 21 -> bone meal, then 13: level 1 + 12 x 3 tenths = level 4, tenths 6
		int[] s = feed(0, 0, 34);
		assertEquals(1, s[2]);
		assertEquals(4, s[0]);
		assertEquals(6, s[1]);
	}
}
