package dev.villageheart.core.raid;

import java.util.Locale;
import java.util.Optional;

/** Where a tracked raid came from (docs/plans/M25.md 3.3). Ids are the lower-case names. */
public enum RaidOrigin {
	BAD_OMEN, RANDOM, TEST;

	public String id() { return name().toLowerCase(Locale.ROOT); }

	public static Optional<RaidOrigin> byId(String id) {
		if (id == null) return Optional.empty();
		for (RaidOrigin o : values()) if (o.id().equals(id) || o.name().equalsIgnoreCase(id)) return Optional.of(o);
		return Optional.empty();
	}
}
