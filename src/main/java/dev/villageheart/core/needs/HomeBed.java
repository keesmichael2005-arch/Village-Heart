package dev.villageheart.core.needs;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;

/**
 * Which bed a worker walks to at home time. The bed they already have is kept, including one that is not in a House.
 * A bed of another building (a Barracks) is not that bed when a House bed is within {@code range} blocks.
 */
public final class HomeBed {
	private HomeBed() {}

	public static Optional<Long> choose(Optional<Long> remembered, boolean otherBuilding, List<Long> houseBeds, Pos here, int range) {
		if (remembered.isPresent() && !otherBuilding) return remembered;
		long limit = (long) range * range;
		Long best = null;
		long bestD = Long.MAX_VALUE;
		for (long packed : houseBeds) {
			long d = dist(here, Pos.unpack(packed));
			if (d > limit || d >= bestD) continue;
			best = packed;
			bestD = d;
		}
		if (best != null) return Optional.of(best);
		return remembered;
	}

	private static long dist(Pos a, Pos b) {
		long dx = (long) a.x() - b.x();
		long dy = (long) a.y() - b.y();
		long dz = (long) a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
