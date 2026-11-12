package dev.villageheart.core.defence;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.blueprint.Rotation4;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A finished defence (docs/plans/M21.md 3.2 #8, M25 3.10). {@code id} is the id of the build that made it.
 * {@code length} is the line length of a wall or a gate (0 for a lamp post or a watchtower). {@code opened} and {@code seen}
 * are ground-relative packed positions: a player-opened cell is not repaired or targeted, and only a seen cell can become breached.
 */
public record DefenceSite(UUID id, String blueprintId, HeartLocation ground, Rotation4 rotation, long builtAt, int length,
		Set<Long> opened, Set<Long> seen) {
	/** A site recorded before M25: length 0, nothing opened, nothing seen. */
	public DefenceSite(UUID id, String blueprintId, HeartLocation ground, Rotation4 rotation, long builtAt) {
		this(id, blueprintId, ground, rotation, builtAt, 0, Set.of(), Set.of());
	}

	public DefenceSite withOpened(Set<Long> opened) {
		return new DefenceSite(id, blueprintId, ground, rotation, builtAt, length, opened, seen);
	}

	public DefenceSite withSeen(Set<Long> seen) {
		return new DefenceSite(id, blueprintId, ground, rotation, builtAt, length, opened, seen);
	}

	public DefenceSite {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(blueprintId, "blueprintId");
		Objects.requireNonNull(ground, "ground");
		Objects.requireNonNull(rotation, "rotation");
		if (length < 0) throw new IllegalArgumentException("length must be >= 0: " + length);
		opened = Set.copyOf(new LinkedHashSet<>(opened));
		seen = Set.copyOf(new LinkedHashSet<>(seen));
	}
}
