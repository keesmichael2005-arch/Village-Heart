package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MarketPlannerTest {
	private static StockItem<String> item(String id, long count, long reserved, int nut) { return new StockItem<>(id, "minecraft:" + id, true, count, reserved, nut); }

	private static MarketInputs<String> in(List<StockItem<String>> stock, Reserves r, long demand, int treasury) {
		return new MarketInputs<>(stock, r, demand, treasury, 3, 0, 0, 0);
	}

	@SuppressWarnings("unchecked")
	private static SaleDecision.Sell<String> sell(SaleDecision d) { return (SaleDecision.Sell<String>) assertInstanceOf(SaleDecision.Sell.class, d); }

	@Test
	void stopOrder() {
		var cobble = List.of(item("cobblestone", 200, 0, 0));
		var capped = new MarketInputs<>(cobble, Reserves.DEFAULT, 0, Balance.MAX_TREASURY, 1, Balance.merchantDailyCap(1), 0, Balance.merchantDailyCap(1)); // M26: the level-1 cap is a tuned value
		assertEquals(new SaleDecision.Stop(StopReason.DAILY_LIMIT), MarketPlanner.decide(capped));
		assertEquals(new SaleDecision.Stop(StopReason.TREASURY_FULL), MarketPlanner.decide(in(cobble, Reserves.DEFAULT, 0, Balance.MAX_TREASURY)));
		sell(MarketPlanner.decide(in(cobble, Reserves.DEFAULT, 0, Balance.MAX_TREASURY - 1)));
		assertEquals(new SaleDecision.Stop(StopReason.NOTHING_TO_SELL), MarketPlanner.decide(in(List.of(item("cobblestone", 64, 0, 0)), Reserves.DEFAULT, 0, 0)));
		assertEquals(new SaleDecision.Stop(StopReason.NOTHING_TO_SELL), MarketPlanner.decide(in(List.of(item("cobblestone", 79, 0, 0)), Reserves.DEFAULT, 0, 0)), "15 < lot");
		assertEquals(new SaleDecision.Stop(StopReason.NOTHING_TO_SELL), MarketPlanner.decide(in(List.of(), Reserves.DEFAULT, 0, 0)));
	}

	@Test
	void choosesMostLotsThenTableOrder() {
		// M26 H3 (cobble and logs cheaper, lots 16 and 8): 140 cobble - 64 = 76 = 4 lots; wheat 164 - 64 = 100 = 5 lots
		var s = List.of(item("cobblestone", 140, 0, 0), item("wheat", 164, 0, 0));
		var d = sell(MarketPlanner.decide(in(s, Reserves.DEFAULT, 0, 0)));
		assertEquals("minecraft:wheat", d.id());
		assertEquals(20, d.lot());
		// 4 lots each: wheat 144 -> 80 = 4, cobble 128 -> 64 = 4, oak log 96 -> 32 = 4; table order: logs, wheat, cobble
		var t = List.of(item("cobblestone", 128, 0, 0), item("wheat", 144, 0, 0), item("oak_log", 96, 0, 0));
		assertEquals("minecraft:oak_log", sell(MarketPlanner.decide(in(t, Reserves.DEFAULT, 0, 0))).id());
		var u = List.of(item("cobblestone", 128, 0, 0), item("wheat", 144, 0, 0));
		assertEquals("minecraft:wheat", sell(MarketPlanner.decide(in(u, Reserves.DEFAULT, 0, 0))).id());
	}

	@Test
	void roadmapReplay() {
		long cobble = 200;
		int sales = 0;
		while (true) {
			var d = MarketPlanner.decide(in(List.of(item("cobblestone", cobble, 0, 0)), Reserves.DEFAULT, 0, 0));
			if (d instanceof SaleDecision.Stop) break;
			cobble -= sell(d).lot();
			sales++;
			assertTrue(cobble >= 64);
		}
		assertEquals(8, sales); // lot 16 since M26 H3 (it was 4 sales of 32)
		assertEquals(72, cobble);
	}

	private static final String[] IDS = {"oak_log", "spruce_log", "wheat", "carrot", "potato", "cobblestone", "coal", "iron_ingot", "cooked_beef", "cooked_cod", "bread", "apple", "stone"};
	private static final int[] NUT = {0, 0, 0, 3, 1, 0, 0, 0, 8, 5, 5, 4, 0};

	/** The oracle is written out here and does not use Surplus. */
	@Test
	void propertyNeverBelowTheReserves() {
		Random rnd = new Random(2022);
		for (int n = 0; n < 2000; n++) {
			long[] count = new long[IDS.length], res = new long[IDS.length];
			for (int i = 0; i < IDS.length; i++) {
				count[i] = rnd.nextInt(4) == 0 ? 0 : rnd.nextInt(400);
				res[i] = rnd.nextInt(4) == 0 ? rnd.nextInt((int) count[i] + 1) : 0;
			}
			int adults = rnd.nextInt(6), babies = rnd.nextInt(4), days = rnd.nextInt(6), itemRes = rnd.nextInt(5) * 32;
			long demand = adults * 10L + babies * 5L;
			Reserves r = new Reserves(days, itemRes);
			int level = 1 + rnd.nextInt(3);
			int soldM = 0;
			for (int guard = 0; guard < 2000; guard++) {
				List<StockItem<String>> stock = new ArrayList<>();
				for (int i = 0; i < IDS.length; i++) stock.add(item(IDS[i], count[i], res[i], NUT[i]));
				var d = MarketPlanner.decide(new MarketInputs<>(stock, r, demand, 0, level, soldM, soldM, soldM));
				if (d instanceof SaleDecision.Stop) break;
				var s = sell(d);
				int idx = Arrays.asList(IDS).indexOf(s.id().substring("minecraft:".length()));
				count[idx] -= s.lot();
				soldM++;
				assertTrue(count[idx] >= res[idx], "claims of others kept");
				if (NUT[idx] == 0) {
					assertTrue(count[idx] >= itemRes + res[idx], "item reserve kept: " + s.id());
				} else if (demand > 0) {
					long pool = 0;
					for (int i = 0; i < IDS.length; i++) pool += (long) NUT[i] * Math.max(0, count[i] - res[i]);
					assertTrue(pool >= days * demand, "food reserve kept");
				}
			}
			assertTrue(soldM <= Balance.merchantDailyCap(level));
		}
	}
}
