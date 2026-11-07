package dev.villageheart.core.building;

import dev.villageheart.core.room.Pos;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Picks the parent building of an outdoor site (DESIGN section 3.3). Pure. */
public final class Links {
	private Links() {}

	public record Candidate(Pos marker, String dimension) {}

	/**
	 * The nearest candidate in the same dimension within {@code range} blocks ({@link Integer#MAX_VALUE} = unlimited), by
	 * squared 3-D distance; ties go to the smaller position (x, then y, then z). Null when there is none.
	 */
	@Nullable
	public static Pos nearest(Pos site, String dimension, List<Candidate> parents, int range) {
		long limit = range == Integer.MAX_VALUE ? Long.MAX_VALUE : (long) range * range;
		Pos best = null;
		long bestD = Long.MAX_VALUE;
		for (Candidate c : parents) {
			if (!c.dimension().equals(dimension)) continue;
			long dx = (long) c.marker().x() - site.x(), dy = (long) c.marker().y() - site.y(), dz = (long) c.marker().z() - site.z();
			long d = dx * dx + dy * dy + dz * dz;
			if (d > limit) continue;
			if (best == null || d < bestD || (d == bestD && c.marker().compareTo(best) < 0)) {
				best = c.marker();
				bestD = d;
			}
		}
		return best;
	}
}
