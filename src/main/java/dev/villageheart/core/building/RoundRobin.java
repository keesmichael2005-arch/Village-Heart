package dev.villageheart.core.building;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which building to check on each tick: one per {@code intervalTicks} in a round-robin over the sorted registry,
 * plus a bounded number of prioritised keys per tick (a fresh placement, a right-click, a dependent of a change).
 * With {@code n} buildings each is checked at least every {@code n * intervalTicks}, and a priority check never delays the cycle.
 * Pure; it counts its own ticks, so the game clock does not matter. At most {@code maxPriorityPerTick + 1} keys per tick.
 */
public final class RoundRobin<K> {
	private final Comparator<K> order;
	private final int intervalTicks;
	private final int maxPriorityPerTick;
	private final LinkedHashSet<K> priority = new LinkedHashSet<>();
	private long ticks;
	private long lastCycle;
	@Nullable
	private K cursor;

	public RoundRobin(Comparator<K> order, int intervalTicks, int maxPriorityPerTick) {
		if (intervalTicks < 1) throw new IllegalArgumentException("intervalTicks must be >= 1");
		if (maxPriorityPerTick < 0) throw new IllegalArgumentException("maxPriorityPerTick must be >= 0");
		this.order = order;
		this.intervalTicks = intervalTicks;
		this.maxPriorityPerTick = maxPriorityPerTick;
	}

	/** Queue {@code key} for an early check (FIFO, de-duplicated). */
	public void prioritise(K key) { priority.add(key); }

	public int pendingPriority() { return priority.size(); }

	/** Advance one tick; the keys to check now, in order. Keys no longer in {@code current} are dropped from the queue. */
	public List<K> tick(SortedSet<K> current) {
		ticks++;
		List<K> out = new ArrayList<>(2);
		for (Iterator<K> it = priority.iterator(); it.hasNext() && out.size() < maxPriorityPerTick; ) {
			K key = it.next();
			it.remove();
			if (current.contains(key)) out.add(key);
		}
		priority.removeIf(k -> !current.contains(k));
		if (ticks - lastCycle >= intervalTicks && !current.isEmpty()) {
			K next = null;
			if (cursor != null) {
				for (K k : current.tailSet(cursor)) {
					if (order.compare(k, cursor) != 0) {
						next = k;
						break;
					}
				}
			}
			if (next == null) next = current.first();
			cursor = next;
			lastCycle = ticks;
			if (!out.contains(next)) out.add(next);
		}
		return out;
	}
}
