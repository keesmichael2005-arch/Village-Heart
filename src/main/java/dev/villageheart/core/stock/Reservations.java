package dev.villageheart.core.stock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Items a holder (a worker's UUID or a build id) has claimed from the stockpile. A claim, not a lock: the items stay in
 * the chests and can be taken by anyone; {@link Stockpile#available} is merely clamped by it.
 */
public final class Reservations<K> {
	private final Map<UUID, Map<K, Integer>> byHolder = new HashMap<>();
	private final Map<K, Integer> totals = new HashMap<>();

	/** {@code n >= 0}; 0 removes. */
	public void set(UUID holder, K key, int n) {
		if (n < 0) throw new IllegalArgumentException("n must be >= 0");
		int old = reservedBy(holder, key);
		if (n == 0) {
			Map<K, Integer> mine = byHolder.get(holder);
			if (mine != null) {
				mine.remove(key);
				if (mine.isEmpty()) byHolder.remove(holder);
			}
		} else {
			byHolder.computeIfAbsent(holder, h -> new HashMap<>()).put(key, n);
		}
		addTotal(key, n - old);
	}

	/** All of a holder's reservations (build finished or cancelled). */
	public void release(UUID holder) {
		Map<K, Integer> mine = byHolder.remove(holder);
		if (mine == null) return;
		for (Map.Entry<K, Integer> e : mine.entrySet()) addTotal(e.getKey(), -e.getValue());
	}

	public int reserved(K key) { return totals.getOrDefault(key, 0); }

	public int reservedBy(UUID holder, K key) {
		Map<K, Integer> mine = byHolder.get(holder);
		return mine == null ? 0 : mine.getOrDefault(key, 0);
	}

	/** Called by {@link Stockpile#withdraw}: the holder's own reservation shrinks by what it took. */
	void consume(UUID holder, K key, int n) {
		int have = reservedBy(holder, key);
		if (have > 0) set(holder, key, Math.max(0, have - n));
	}

	private void addTotal(K key, int delta) {
		if (delta == 0) return;
		int now = totals.getOrDefault(key, 0) + delta;
		if (now <= 0) totals.remove(key); else totals.put(key, now);
	}
}
