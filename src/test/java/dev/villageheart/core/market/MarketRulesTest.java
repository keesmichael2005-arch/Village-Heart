package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Balance;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketRulesTest {
	private static MarketInputs<String> in(int level, int byMerchant, int atStall, int village) {
		return new MarketInputs<>(List.of(new StockItem<>("c", "minecraft:cobblestone", true, 2000, 0, 0)), Reserves.DEFAULT, 0, 0, level, byMerchant, atStall, village);
	}

	// M26: the caps are a tuned Balance value, so these tests follow Balance and keep the rules that never move: the caps rise with level,
	// level 0 and 9 clamp to level 1 and 3, and every merchant cap stays below the village's (M26 H1, docs/M26-results.md: 8 / 12 / 16 left the
	// 10-worker payroll 2 emeralds short a day and the village struck from day 9).
	private static final int C1 = Balance.merchantDailyCap(1), C2 = Balance.merchantDailyCap(2), C3 = Balance.merchantDailyCap(3);

	@Test
	void capsByLevel() {
		assertEquals(C1, MarketRules.dailyCap(1));
		assertEquals(C2, MarketRules.dailyCap(2));
		assertEquals(C3, MarketRules.dailyCap(3));
		assertEquals(C1, MarketRules.dailyCap(0));
		assertEquals(C3, MarketRules.dailyCap(9));
		assertTrue(C1 < C2 && C2 < C3 && C3 <= Balance.MARKET_VILLAGE_DAILY_CAP, "caps rise by level and stay under the village cap");
	}

	@Test
	void plannerFollowsTheCaps() {
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(1, C1 - 1, 0, C1 - 1)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(1, C1, 0, C1)));
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(2, C1, 0, C1)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(2, C2, 0, C2)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(1, 0, C1, C1)), "the stall's count caps too");
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(3, C3 - 1, C3 - 1, C3 - 1)));
	}

	@Test
	void villageCap() {
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(3, 0, 0, 31)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(3, 0, 0, 32)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(3, 1, 1, 40)));
	}

	@Test
	void midDayLevelUpRaisesTheCapAtOnce() {
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(1, C1, C1, C1)));
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(2, C1, C1, C1)));
	}

	@Test
	void underCapsOnPlainNumbers() {
		assertTrue(MarketRules.underCaps(0, C1 - 1, C1 - 1, 1));
		assertFalse(MarketRules.underCaps(0, C1, C1, 1));
	}
}
