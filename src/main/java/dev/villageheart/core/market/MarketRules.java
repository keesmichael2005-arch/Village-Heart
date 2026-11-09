package dev.villageheart.core.market;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import java.util.UUID;

/** The daily caps (M22 plan 3.2 #5): per merchant by level, per stall, and one for the whole village. */
public final class MarketRules {
	private MarketRules() {}

	public static int dailyCap(int level) { return Balance.merchantDailyCap(level); }

	public static boolean underCaps(int soldByMerchant, int soldAtStall, int soldByVillage, int level) {
		return Math.max(soldByMerchant, soldAtStall) < dailyCap(level) && soldByVillage < Balance.MARKET_VILLAGE_DAILY_CAP;
	}

	public static boolean underCaps(SalesLedger today, UUID merchant, HeartLocation stall, int level) {
		return underCaps(today.soldBy(merchant), today.soldAt(stall), today.soldToday(), level);
	}
}
