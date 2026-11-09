package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarketRulesTest {
	private static MarketInputs<String> in(int level, int byMerchant, int atStall, int village) {
		return new MarketInputs<>(List.of(new StockItem<>("c", "minecraft:cobblestone", true, 2000, 0, 0)), Reserves.DEFAULT, 0, 0, level, byMerchant, atStall, village);
	}

	@Test
	void capsByLevel() {
		assertEquals(8, MarketRules.dailyCap(1));
		assertEquals(12, MarketRules.dailyCap(2));
		assertEquals(16, MarketRules.dailyCap(3));
		assertEquals(8, MarketRules.dailyCap(0));
		assertEquals(16, MarketRules.dailyCap(9));
	}

	@Test
	void plannerFollowsTheCaps() {
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(1, 7, 0, 7)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(1, 8, 0, 8)));
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(2, 8, 0, 8)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(2, 12, 0, 12)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(1, 0, 8, 8)), "the stall's count caps too");
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(3, 15, 15, 15)));
	}

	@Test
	void villageCap() {
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(3, 0, 0, 31)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(3, 0, 0, 32)));
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(3, 1, 1, 40)));
	}

	@Test
	void midDayLevelUpRaisesTheCapAtOnce() {
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(in(1, 8, 8, 8)));
		assertInstanceOf(SaleDecision.Sell.class, MarketPlanner.decide(in(2, 8, 8, 8)));
	}

	@Test
	void underCapsOnPlainNumbers() {
		assertTrue(MarketRules.underCaps(0, 7, 7, 1));
		assertFalse(MarketRules.underCaps(0, 8, 8, 1));
	}
}
