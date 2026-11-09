package dev.villageheart.core.guard;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Which guard sleeps in which bed of a Barracks (M18 3.3): a guard keeps its bed while it is still a bed of the Barracks, the others get
 * the free beds in position order. A guard with no bed left is absent from the result (status "No bed").
 */
public final class GuardBeds {
	private GuardBeds() {}

	public static Map<UUID, Pos> assign(List<Pos> beds, List<UUID> guardsInHiringOrder, Map<UUID, Pos> current) {
		Set<Pos> valid = new HashSet<>(beds);
		Map<UUID, Pos> out = new LinkedHashMap<>();
		Set<Pos> taken = new HashSet<>();
		for (UUID g : guardsInHiringOrder) {
			Pos p = current.get(g);
			if (p != null && valid.contains(p) && taken.add(p)) out.put(g, p);
		}
		List<Pos> free = new ArrayList<>(new TreeSet<>(beds));
		free.removeAll(taken);
		int i = 0;
		for (UUID g : guardsInHiringOrder) {
			if (out.containsKey(g)) continue;
			if (i >= free.size()) break;
			out.put(g, free.get(i++));
		}
		return out;
	}
}
