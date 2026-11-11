package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cracked and breached defence cells (docs/plans/M25.md 3.9). Cracks hold damage {@code 1..HP-1}; a cell at its hit points is breached.
 * At most {@link Balance#MAX_DAMAGED_CELLS} entries: breaches are kept first, then the most-damaged cracks.
 */
public record DefenceDamage(Map<Long, Integer> cracked, List<Long> breached) {
	public static final DefenceDamage EMPTY = new DefenceDamage(Map.of(), List.of());

	public DefenceDamage {
		cracked = Map.copyOf(cracked);
		breached = List.copyOf(breached);
	}

	public int size() { return cracked.size() + breached.size(); }

	public boolean isEmpty() { return size() == 0; }

	public int damageAt(long packed) { return cracked.getOrDefault(packed, 0); }

	public boolean isBreached(long packed) { return breached.contains(packed); }

	/**
	 * One hit on a cell. A breached cell is unchanged. An unbreakable cell stops at {@code hp - 1}. At {@code hp} a breakable cell
	 * becomes a breach and its crack is cleared.
	 */
	public DefenceDamage struck(long packed, int hit, int hp, boolean breakable) {
		if (isBreached(packed) || hit <= 0) return this;
		int next = damageAt(packed) + hit;
		java.util.Map<Long, Integer> cracks = new LinkedHashMap<>(cracked);
		java.util.List<Long> holes = new ArrayList<>(breached);
		if (next >= hp) {
			if (!breakable) {
				cracks.put(packed, Math.max(1, hp - 1));
				return new DefenceDamage(cracks, holes);
			}
			cracks.remove(packed);
			if (!holes.contains(packed)) holes.add(packed);
			return new DefenceDamage(cracks, holes);
		}
		cracks.put(packed, next);
		return new DefenceDamage(cracks, holes);
	}

	/** Flat {@code [pos, damage, ...]} in packed-position order, the on-disk shape. */
	public List<Long> flatCracked() {
		List<Long> out = new ArrayList<>(cracked.size() * 2);
		cracked.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
			out.add(e.getKey());
			out.add(e.getValue().longValue());
		});
		return out;
	}

	/**
	 * Keeps every breach that fits, then the most-damaged cracks, up to {@link Balance#MAX_DAMAGED_CELLS}.
	 * A cell listed as both is a breach. {@code trimmed} is how many entries were dropped.
	 */
	public static Capped cap(Map<Long, Integer> crackedIn, Collection<Long> breachedIn) {
		List<Long> breaches = new ArrayList<>();
		for (long p : breachedIn) if (!breaches.contains(p)) breaches.add(p);
		breaches.sort(Long::compare);
		Map<Long, Integer> cracks = new LinkedHashMap<>();
		for (var e : crackedIn.entrySet()) {
			if (e.getValue() == null || breaches.contains(e.getKey())) continue;
			cracks.put(e.getKey(), e.getValue());
		}
		int before = cracks.size() + breaches.size();
		if (breaches.size() > Balance.MAX_DAMAGED_CELLS) breaches = new ArrayList<>(breaches.subList(0, Balance.MAX_DAMAGED_CELLS));
		int room = Balance.MAX_DAMAGED_CELLS - breaches.size();
		List<Map.Entry<Long, Integer>> ordered = new ArrayList<>(cracks.entrySet());
		ordered.sort(Comparator.<Map.Entry<Long, Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparingLong(Map.Entry::getKey));
		Map<Long, Integer> kept = new LinkedHashMap<>();
		for (int i = 0; i < ordered.size() && kept.size() < room; i++) kept.put(ordered.get(i).getKey(), ordered.get(i).getValue());
		DefenceDamage damage = new DefenceDamage(kept, breaches);
		return new Capped(damage, before - damage.size());
	}

	/** @param trimmed entries dropped to fit the cap; 0 when everything fit */
	public record Capped(DefenceDamage damage, int trimmed) {}
}
