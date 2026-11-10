package dev.villageheart.core.guard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits the guards into a night and a day shift (M18 r3, owner rule): up to 3 guards ALL work the night (no day shift); from the 4th guard on
 * the day keeps {@code floor(n / 4)} guards (4-7 guards: 1, 8-11: 2), the rest work nights, so nights always have at least 3 once there are 3 or
 * more. The most senior guards (level, then XP) take the nights; a new guard has the least seniority, so it takes an open day slot, and moves to
 * the night as it gains XP or when a night guard falls. Ties keep the side a guard is on, then the hiring order, so the shifts do not flap.
 * Pinned guards (the player's Shift button) never move and count toward their side. In daytime with no day guard the alarm is the only cover.
 */
public final class ShiftBalancer {
	private ShiftBalancer() {}

	/** A guard as the balancer sees it: hiring order is the list order. */
	public record Guard(UUID id, int level, int xp) {}

	/** How many guards of {@code n} work the day: none below 4, then one per four. */
	public static int dayCount(int n) { return n / 4; }

	public static Map<UUID, GuardShift> assign(List<Guard> guardsInHiringOrder, Map<UUID, GuardShift> current) {
		int n = guardsInHiringOrder.size();
		int nightWant = n - dayCount(n);
		Map<UUID, GuardShift> pinned = new LinkedHashMap<>();
		List<Guard> free = new ArrayList<>();
		int pinnedNight = 0;
		Map<UUID, Integer> order = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			Guard g = guardsInHiringOrder.get(i);
			order.put(g.id(), i);
			GuardShift s = current.get(g.id());
			if (s != null && s.pinned()) {
				pinned.put(g.id(), s);
				if (s.shift() == Shift.NIGHT) pinnedNight++;
			} else {
				free.add(g);
			}
		}
		free.sort(Comparator.<Guard>comparingInt(Guard::level).reversed()
				.thenComparing(Comparator.<Guard>comparingInt(Guard::xp).reversed())
				.thenComparingInt(g -> current.get(g.id()) != null && current.get(g.id()).shift() == Shift.NIGHT ? 0 : 1)
				.thenComparingInt(g -> order.get(g.id())));
		int nightSlots = Math.max(0, Math.min(free.size(), nightWant - pinnedNight));
		Map<UUID, GuardShift> assigned = new LinkedHashMap<>();
		for (int i = 0; i < free.size(); i++) assigned.put(free.get(i).id(), new GuardShift(i < nightSlots ? Shift.NIGHT : Shift.DAY, false));
		Map<UUID, GuardShift> out = new LinkedHashMap<>();
		for (Guard g : guardsInHiringOrder) out.put(g.id(), pinned.containsKey(g.id()) ? pinned.get(g.id()) : assigned.get(g.id()));
		return out;
	}

	/** As {@link #assign} with every guard at level 1 and no XP: the side a guard is on and the hiring order decide. */
	public static Map<UUID, GuardShift> rebalance(List<UUID> guardsInHiringOrder, Map<UUID, GuardShift> current) {
		List<Guard> gs = new ArrayList<>();
		for (UUID id : guardsInHiringOrder) gs.add(new Guard(id, 1, 0));
		return assign(gs, current);
	}
}
