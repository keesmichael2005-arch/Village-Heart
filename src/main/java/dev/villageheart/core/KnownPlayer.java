package dev.villageheart.core;

import java.util.Objects;
import java.util.UUID;

/**
 * A player the village has seen: the last known name (the village's name book) and when it was last seen
 * (game time). The name is stored without formatting codes and cut to {@link Balance#MAX_PLAYER_NAME}; it may be
 * empty (a placeholder for a ranked player who has not joined since the name book existed).
 */
public record KnownPlayer(UUID id, String name, long lastSeen) {
	public KnownPlayer {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		name = cleanName(name);
	}

	/** Strip the section sign (formatting codes), trim, and cut to the maximum length. */
	public static String cleanName(String raw) {
		String s = raw.replace("§", "").strip();
		if (s.length() > Balance.MAX_PLAYER_NAME) s = s.substring(0, Balance.MAX_PLAYER_NAME).stripTrailing();
		return s;
	}
}
