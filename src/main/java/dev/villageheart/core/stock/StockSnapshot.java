package dev.villageheart.core.stock;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * What the stockpile held at one moment.
 *
 * @param totals           one per variant, count above 0; sorted by count (descending), then first seen (site, container, slot)
 * @param sites            valid Storage buildings with at least one container read this time
 * @param unknownSites     valid Storage whose contents are unknown (no scan yet, an unscanned neighbour, no readable container)
 * @param containers       stockpile containers read this time
 * @param unreadContainers listed but skipped (chunk not loaded, or no chest/barrel there any more)
 * @param slots            slots over the containers read
 * @param freeSlots        empty slots over the containers read
 */
public record StockSnapshot<K>(long gameTime, List<Total<K>> totals, int sites, int unknownSites, int containers,
		int unreadContainers, int slots, int freeSlots, StorageAlert alert) {
	public record Total<K>(K key, long count) {}

	public StockSnapshot {
		totals = List.copyOf(totals);
	}

	public long count(K key) {
		for (Total<K> t : totals) if (t.key().equals(key)) return t.count();
		return 0;
	}

	/** Totals summed per {@code id(key)}, sorted by id. */
	public <I extends Comparable<? super I>> Map<I, Long> totalsBy(Function<? super K, I> id) {
		Map<I, Long> out = new TreeMap<>();
		for (Total<K> t : totals) out.merge(id.apply(t.key()), t.count(), Long::sum);
		return out;
	}
}
