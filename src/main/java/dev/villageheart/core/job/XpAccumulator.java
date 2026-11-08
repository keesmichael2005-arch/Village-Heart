package dev.villageheart.core.job;

import dev.villageheart.core.Balance;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fractional XP per worker (a builder's block is 0.2 XP). Sums integer thousandths of an XP as {@code long}, so a fifth never drifts.
 * Keyed by villager UUID; not saved (a restart loses less than one XP). Server thread only.
 */
public final class XpAccumulator {
	private final Map<UUID, Long> milli = new HashMap<>();

	/** Adds {@code units} of {@code unit} for the worker and returns the whole XP to award now; the remainder carries. */
	public int add(UUID worker, WorkUnit unit, int units) {
		if (units < 0) throw new IllegalArgumentException("units must be >= 0: " + units);
		long total = milli.getOrDefault(worker, 0L) + (long) Balance.milliXpPerUnit(unit) * units;
		long whole = total / 1000;
		long rest = total % 1000;
		if (rest == 0) milli.remove(worker); else milli.put(worker, rest);
		return (int) Math.min(whole, Integer.MAX_VALUE);
	}

	public void forget(UUID worker) { milli.remove(worker); }

	public void clear() { milli.clear(); }
}
