package dev.villageheart.core.market;

import dev.villageheart.core.HeartLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Emeralds the market has earned today, by merchant and by stall (saved). A new day index empties it. Immutable. */
public record SalesLedger(long day, Map<UUID, Integer> byMerchant, Map<HeartLocation, Integer> byStall) {
	public SalesLedger {
		byMerchant = Map.copyOf(byMerchant);
		byStall = Map.copyOf(byStall);
	}

	public static SalesLedger empty(long day) { return new SalesLedger(day, Map.of(), Map.of()); }

	/** {@code !=}, not {@code >}: /time set can move the day back. */
	public SalesLedger forDay(long today) { return today == day ? this : empty(today); }

	public int soldBy(UUID merchant) { return byMerchant.getOrDefault(merchant, 0); }

	public int soldAt(HeartLocation stall) { return byStall.getOrDefault(stall, 0); }

	public SalesLedger record(UUID merchant, HeartLocation stall, int emeralds) {
		if (emeralds < 0) throw new IllegalArgumentException("emeralds must be >= 0: " + emeralds);
		Map<UUID, Integer> m = new HashMap<>(byMerchant);
		m.merge(merchant, emeralds, Integer::sum);
		Map<HeartLocation, Integer> s = new HashMap<>(byStall);
		s.merge(stall, emeralds, Integer::sum);
		return new SalesLedger(day, m, s);
	}

	public int soldToday() {
		long sum = 0;
		for (int v : byMerchant.values()) sum += v;
		return (int) Math.min(sum, Integer.MAX_VALUE);
	}
}
