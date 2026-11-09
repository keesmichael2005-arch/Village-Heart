package dev.villageheart.core.market;

import dev.villageheart.core.economy.TreasuryMath;

/** Which lot is sold next (M22 plan 3.2 #4): stop reasons first, then the item with the most whole lots of surplus (ties: table order). */
public final class MarketPlanner {
	private MarketPlanner() {}

	public static <K> SaleDecision decide(MarketInputs<K> in) {
		if (!MarketRules.underCaps(in.soldByMerchant(), in.soldAtStall(), in.soldByVillage(), in.level())) return new SaleDecision.Stop(StopReason.DAILY_LIMIT);
		if (TreasuryMath.depositable(in.treasury(), 1) == 0) return new SaleDecision.Stop(StopReason.TREASURY_FULL);
		StockItem<K> best = null;
		long bestLots = 0;
		int bestLot = 0;
		for (StockItem<K> item : in.stock()) {
			var lot = PriceTable.lot(item.id());
			if (lot.isEmpty()) continue;
			long lots = Surplus.of(item, in.stock(), in.reserves(), in.demand()) / lot.getAsInt();
			if (lots <= 0) continue;
			if (best == null || lots > bestLots || (lots == bestLots && PriceTable.order(item.id()) < PriceTable.order(best.id()))) {
				best = item;
				bestLots = lots;
				bestLot = lot.getAsInt();
			}
		}
		if (best == null) return new SaleDecision.Stop(StopReason.NOTHING_TO_SELL);
		return new SaleDecision.Sell<>(best.key(), best.id(), bestLot);
	}
}
