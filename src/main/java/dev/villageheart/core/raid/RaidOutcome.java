package dev.villageheart.core.raid;

import java.util.Locale;
import java.util.Optional;

/** How a tracked raid ended. Saved by {@link #id()}. */
public enum RaidOutcome {
	VICTORY, LOSS, STOPPED, GONE;

	public String id() { return name().toLowerCase(Locale.ROOT); }

	public static Optional<RaidOutcome> byId(String id) {
		if (id == null) return Optional.empty();
		for (RaidOutcome o : values()) if (o.id().equals(id)) return Optional.of(o);
		return Optional.empty();
	}
}
