package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.job.ToolTier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The roadmap's L1 statistics (clarified 2026-11-08): fixed counts and entries with a chance of at least 30% within 5% of the expected
 * mean over 10 000 rolls; every entry, rare ones included, within 5% over 400 000 rolls (a 2% entry has a standard error of 7% of its
 * mean over 10 000 rolls, so that check would depend on the seed; over 400 000 rolls it is 4.4 sigma). Diamonds only with an iron pickaxe
 * or better and only in the DEEP band.
 */
class HaulRollStatsTest {
	private static final UUID WORKER = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

	private static Map<String, Double> means(DepthBand b, ToolTier t, int rolls) {
		Map<String, Long> sum = new HashMap<>();
		for (int i = 0; i < rolls; i++) {
			HaulTable.roll(b, t, HaulRng.forHaul(42, WORKER, i)).forEach((k, v) -> sum.merge(k, (long) v, Long::sum));
		}
		Map<String, Double> out = new HashMap<>();
		sum.forEach((k, v) -> out.put(k, v / (double) rolls));
		return out;
	}

	private static void within5Percent(DepthBand b, ToolTier t, int rolls, boolean onlyFrequent) {
		Map<String, Double> got = means(b, t, rolls);
		Map<String, Double> want = HaulTable.expected(b, t);
		for (HaulEntry e : HaulTable.entries(b)) {
			if (onlyFrequent && !(e.fixed() || e.chance() >= 0.3)) continue;
			double expected = want.get(e.itemId());
			if (expected == 0.0) {
				assertFalse(got.containsKey(e.itemId()), b + " " + t + " must not give " + e.itemId());
				continue;
			}
			double mean = got.getOrDefault(e.itemId(), 0.0);
			assertEquals(expected, mean, expected * 0.05, b + " " + t + " " + e.itemId() + " over " + rolls + " rolls: mean " + mean);
		}
	}

	@Test
	void tenThousandRollsHoldFixedAndFrequentEntriesWithinFivePercent() {
		for (DepthBand b : DepthBand.values()) for (ToolTier t : ToolTier.values()) within5Percent(b, t, 10_000, true);
	}

	@Test
	void fourHundredThousandRollsHoldEveryEntryWithinFivePercent() {
		for (DepthBand b : DepthBand.values()) for (ToolTier t : ToolTier.values()) within5Percent(b, t, 400_000, false);
	}

	@Test
	void diamondsOnlyDeepAndOnlyWithIronOrBetter() {
		for (DepthBand b : DepthBand.values()) {
			for (ToolTier t : ToolTier.values()) {
				boolean allowed = b == DepthBand.DEEP && t.ordinal() >= ToolTier.IRON.ordinal();
				long diamonds = 0;
				for (int i = 0; i < 400_000; i++) diamonds += HaulTable.roll(b, t, HaulRng.forHaul(42, WORKER, i)).getOrDefault("minecraft:diamond", 0);
				if (allowed) assertTrue(diamonds > 0, b + " " + t + " should find diamonds");
				else assertEquals(0, diamonds, b + " " + t + " must find none");
			}
		}
	}

	@Test
	void theSameHaulWithAStoneAndAnIronPickaxeDiffersOnlyInTheDiamond() {
		for (int i = 0; i < 20_000; i++) {
			Map<String, Integer> stone = new HashMap<>(HaulTable.roll(DepthBand.DEEP, ToolTier.STONE, HaulRng.forHaul(5, WORKER, i)));
			Map<String, Integer> iron = new HashMap<>(HaulTable.roll(DepthBand.DEEP, ToolTier.IRON, HaulRng.forHaul(5, WORKER, i)));
			iron.remove("minecraft:diamond");
			assertEquals(stone, iron, "haul " + i);
		}
	}
}
