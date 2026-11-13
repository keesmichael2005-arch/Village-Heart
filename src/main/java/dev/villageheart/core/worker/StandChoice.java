package dev.villageheart.core.worker;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;

/**
 * Which open neighbour of a block a villager stands on. The caller has already dropped the blocked sides. Ties keep the
 * earlier entry, so the caller lists them in a stable order.
 */
public final class StandChoice {
	private StandChoice() {}

	public static Optional<Pos> nearest(List<Pos> open, Pos from) {
		Pos best = null;
		long bestD = Long.MAX_VALUE;
		for (Pos p : open) {
			long d = dist2(from, p);
			if (d < bestD) {
				best = p;
				bestD = d;
			}
		}
		return Optional.ofNullable(best);
	}

	private static long dist2(Pos a, Pos b) {
		long dx = (long) a.x() - b.x(), dy = (long) a.y() - b.y(), dz = (long) a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
