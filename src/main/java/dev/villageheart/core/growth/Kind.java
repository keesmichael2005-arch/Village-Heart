package dev.villageheart.core.growth;

import java.util.Objects;

/** A villager's role and whether the village has a meeting point (a bell) for it. */
public record Kind(Role role, boolean hasMeetingPoint) {
	public Kind { Objects.requireNonNull(role, "role"); }
}
