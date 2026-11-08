package dev.villageheart.core.tree;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The order a tree is felled in: top down, so an interrupted fell can always be resumed by flooding from the base. */
public final class FellOrder {
	private FellOrder() {}

	/** y descending, then horizontal distance to the first base cell descending, then position; the base cells come last (position order). */
	public static List<Pos> topDown(Collection<Pos> logs, List<Pos> base) {
		Set<Pos> baseSet = new HashSet<>(base);
		Pos ref = base.isEmpty() ? null : base.get(0);
		List<Pos> rest = new ArrayList<>();
		List<Pos> tail = new ArrayList<>();
		for (Pos p : logs) (baseSet.contains(p) ? tail : rest).add(p);
		Comparator<Pos> byY = Comparator.comparingInt(Pos::y).reversed();
		Comparator<Pos> byDist = Comparator.comparingLong((Pos p) -> ref == null ? 0L : d2(p, ref)).reversed();
		rest.sort(byY.thenComparing(byDist).thenComparing(Comparator.naturalOrder()));
		tail.sort(Comparator.naturalOrder());
		rest.addAll(tail);
		return rest;
	}

	private static long d2(Pos a, Pos b) {
		long dx = a.x() - b.x(), dz = a.z() - b.z();
		return dx * dx + dz * dz;
	}
}
