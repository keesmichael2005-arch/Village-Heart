package dev.villageheart.core.heart;

import java.util.Optional;
import java.util.Set;

/**
 * How a death-log cause id becomes a lang key (M25). Entity ids such as {@code minecraft:vindicator} use
 * {@code entity.<namespace>.<path>}. {@code zombified} and the common damage ids use
 * {@code death.villageheart.cause.<id>}. Anything else is shown as the raw id.
 */
public final class DeathCauseText {
	/** Damage-type paths, plus {@code zombified}, that have a villageheart cause line. */
	public static final Set<String> NAMED = Set.of("zombified", "fall", "drown", "in_fire", "on_fire", "lava", "starve",
			"generic_kill", "magic", "explosion", "player_explosion", "arrow");

	private DeathCauseText() {}

	/** A translation key, or empty when the screen should show the raw id. */
	public static Optional<String> translationKey(String cause) {
		if (cause == null || cause.isEmpty()) return Optional.empty();
		int colon = cause.indexOf(':');
		String path = colon >= 0 ? cause.substring(colon + 1) : cause;
		if (path.isEmpty()) return Optional.empty();
		boolean vanilla = colon < 0 || cause.startsWith("minecraft:");
		if (vanilla && NAMED.contains(path)) return Optional.of("death.villageheart.cause." + path);
		if (colon > 0) return Optional.of("entity." + cause.substring(0, colon) + "." + path);
		return Optional.empty();
	}
}
