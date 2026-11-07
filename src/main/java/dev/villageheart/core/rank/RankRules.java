package dev.villageheart.core.rank;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.Rejection;
import java.util.Optional;
import java.util.UUID;

/**
 * Whether a requested rank change or ownership transfer is allowed. Checked after the action gate (permission) by the
 * screen, the payloads and the op commands alike, so they cannot drift apart.
 */
public final class RankRules {
	private RankRules() {}

	/**
	 * @param online whether the target is on the server now (an online stranger can be ranked; an offline one must
	 *               already be in the village's name book)
	 */
	public static Optional<Rejection> setRank(VillageState state, UUID target, Rank rank, boolean online) {
		if (rank == Rank.OWNER) return Optional.of(Rejection.BAD_RANK);
		if (state.owner().equals(target)) return Optional.of(Rejection.OWNER_RANK_FIXED);
		boolean knownToVillage = state.knownPlayer(target).isPresent();
		if (!knownToVillage && !online) return Optional.of(Rejection.UNKNOWN_PLAYER);
		Rank current = state.rankOf(target);
		if (current == rank) return Optional.empty();
		if (rank != Rank.VISITOR && current == Rank.VISITOR && state.ranks().size() >= Balance.MAX_KNOWN_PLAYERS - 1) {
			return Optional.of(Rejection.TOO_MANY_MEMBERS);
		}
		return Optional.empty();
	}

	/**
	 * @param expected the rank the actor's screen showed for the target ({@code Optional.empty()} for the op command);
	 *                 a different current rank means the target changed under the confirmation
	 */
	public static Optional<Rejection> transfer(VillageState state, UUID actor, UUID target, Optional<Rank> expected) {
		if (target.equals(actor) || state.owner().equals(target)) return Optional.of(Rejection.OWNER_RANK_FIXED);
		Rank current = state.rankOf(target);
		if (expected.isPresent() && expected.get() != current) return Optional.of(Rejection.TARGET_CHANGED);
		if (current != Rank.STEWARD && current != Rank.RESIDENT) return Optional.of(Rejection.NOT_A_MEMBER);
		return Optional.empty();
	}
}
