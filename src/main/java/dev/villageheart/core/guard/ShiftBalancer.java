package dev.villageheart.core.guard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits the guards into a day and a night shift with as few moves as possible (M18 3.3). Guards that already have a shift keep it; a new
 * guard goes to the side with fewer guards (a tie goes to NIGHT: night is when the mobs come); entries of guards that are gone are dropped.
 * Then, while the two sides differ by more than one, the most recently hired unpinned guard of the bigger side moves across. Pins never move.
 */
public final class ShiftBalancer {
	private ShiftBalancer() {}

	public static Map<UUID, GuardShift> rebalance(List<UUID> guardsInHiringOrder, Map<UUID, GuardShift> current) {
		Map<UUID, GuardShift> kept = new LinkedHashMap<>();
		int[] count = new int[2];
		for (UUID g : guardsInHiringOrder) {
			GuardShift s = current.get(g);
			if (s != null && !kept.containsKey(g)) {
				kept.put(g, s);
				count[s.shift().ordinal()]++;
			}
		}
		Map<UUID, GuardShift> out = new LinkedHashMap<>();
		for (UUID g : guardsInHiringOrder) {
			if (out.containsKey(g)) continue;
			GuardShift s = kept.get(g);
			if (s == null) {
				Shift side = count[Shift.NIGHT.ordinal()] <= count[Shift.DAY.ordinal()] ? Shift.NIGHT : Shift.DAY;
				s = new GuardShift(side, false);
				count[side.ordinal()]++;
			}
			out.put(g, s);
		}
		while (Math.abs(count[0] - count[1]) > 1) {
			Shift bigger = count[Shift.DAY.ordinal()] > count[Shift.NIGHT.ordinal()] ? Shift.DAY : Shift.NIGHT;
			UUID mover = null;
			for (UUID g : guardsInHiringOrder) {
				GuardShift s = out.get(g);
				if (!s.pinned() && s.shift() == bigger) mover = g;
			}
			if (mover == null) break;
			out.put(mover, new GuardShift(bigger.other(), false));
			count[bigger.ordinal()]--;
			count[bigger.other().ordinal()]++;
		}
		return out;
	}
}
