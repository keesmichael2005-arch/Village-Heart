package dev.villageheart.core.heart;

import dev.villageheart.core.VillageState;
import java.util.Optional;
import java.util.UUID;

/** The checks every Heart screen action passes before it touches the world. First failure wins. */
public final class ActionGate {
	private ActionGate() {}

	/**
	 * Order: rate limit (so a flood of bad packets stays cheap), open screen, current village, a village at all, then
	 * the rank, which is re-read from the current village state.
	 */
	public static Optional<Rejection> check(boolean allowedByRate, boolean menuOpen, boolean sameEpoch,
			Optional<VillageState> village, UUID player, HeartAction action) {
		if (!allowedByRate) return Optional.of(Rejection.RATE_LIMITED);
		if (!menuOpen) return Optional.of(Rejection.NOT_OPEN);
		if (!sameEpoch) return Optional.of(Rejection.STALE);
		if (village.isEmpty()) return Optional.of(Rejection.NO_VILLAGE);
		// one place for "no honest client sends this" (M12's BAD_AMOUNT goes here too)
		Optional<Rejection> malformed = action.malformed();
		if (malformed.isPresent()) return malformed;
		if (!Permissions.allows(village.get().rankOf(player), action.permission())) return Optional.of(Rejection.NO_PERMISSION);
		return Optional.empty();
	}
}
