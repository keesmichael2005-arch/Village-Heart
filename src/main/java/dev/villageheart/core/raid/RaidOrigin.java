package dev.villageheart.core.raid;

import java.util.Locale;
import java.util.Optional;

/** Who started the raid. Saved by {@link #id()}. */
public enum RaidOrigin {
	BAD_OMEN, RANDOM, TEST;

	public String id() { return name().toLowerCase(Locale.ROOT); }

	public static Optional<RaidOrigin> byId(String id) {
		if (id == null) return Optional.empty();
		for (RaidOrigin o : values()) if (o.id().equals(id)) return Optional.of(o);
		return Optional.empty();
	}
}
