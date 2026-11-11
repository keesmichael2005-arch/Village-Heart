package dev.villageheart.core.raid;

import java.util.Locale;
import java.util.Optional;

/** How a tracked raid ended (docs/plans/M25.md 3.3). Ids are the lower-case names. */
public enum RaidOutcome {
	VICTORY, LOSS, STOPPED, GONE;

	public String id() { return name().toLowerCase(Locale.ROOT); }

	public static Optional<RaidOutcome> byId(String id) {
		if (id == null) return Optional.empty();
		for (RaidOutcome o : values()) if (o.id().equals(id) || o.name().equalsIgnoreCase(id)) return Optional.of(o);
		return Optional.empty();
	}
}
