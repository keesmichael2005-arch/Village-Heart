package dev.villageheart.core.growth;

import java.util.Objects;
import java.util.UUID;

/** An adult resident the dawn decision looks at. {@code ready} = fed, housed and past its breeding cooldown ({@link GrowthRules#ready}). */
public record Candidate(UUID id, double x, double y, double z, boolean ready, Kind kind) {
	public Candidate {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
	}

	double distanceSq(Candidate o) {
		double dx = x - o.x, dy = y - o.y, dz = z - o.z;
		return dx * dx + dy * dy + dz * dz;
	}
}
