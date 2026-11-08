package dev.villageheart.core.defence;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.blueprint.Rotation4;
import java.util.Objects;
import java.util.UUID;

/**
 * A finished lamp post or watchtower (docs/plans/M21.md 3.2 #8), saved with the village so guards can patrol there (M18). {@code id} is the
 * id of the build that made it.
 */
public record DefenceSite(UUID id, String blueprintId, HeartLocation ground, Rotation4 rotation, long builtAt) {
	public DefenceSite {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(blueprintId, "blueprintId");
		Objects.requireNonNull(ground, "ground");
		Objects.requireNonNull(rotation, "rotation");
	}
}
