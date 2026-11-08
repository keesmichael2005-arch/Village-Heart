package dev.villageheart.core.mine;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Which Mineshaft a miner uses: the nearest of the valid ones linked to its Mine; a tie goes to the lowest location. */
public final class ShaftChoice {
	private ShaftChoice() {}

	private static long distSq(HeartLocation a, HeartLocation b) {
		long dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}

	public static Optional<HeartLocation> pick(HeartLocation mine, List<HeartLocation> linkedValid) {
		return linkedValid.stream()
				.filter(s -> s.dimension().equals(mine.dimension()))
				.min(Comparator.<HeartLocation>comparingLong(s -> distSq(mine, s)).thenComparing(VillageState.locationOrder()));
	}
}
