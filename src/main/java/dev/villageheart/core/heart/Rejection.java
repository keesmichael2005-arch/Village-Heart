package dev.villageheart.core.heart;

import java.util.Locale;

/** Why the server refused a Heart screen action. */
public enum Rejection {
	NOT_OPEN, STALE, NO_VILLAGE, NO_PERMISSION, RATE_LIMITED, UNKNOWN_JOB, VILLAGER_NOT_FOUND, NOT_A_BUILDING,
	BAD_RANK, UNKNOWN_PLAYER, OWNER_RANK_FIXED, TOO_MANY_MEMBERS, NOT_A_MEMBER, TARGET_CHANGED, BAD_AMOUNT, BAD_SHIFT, NOT_A_GUARD;

	public String id() { return name().toLowerCase(Locale.ROOT); }

	/** Lang key of the reply text. */
	public String langKey() { return "message.villageheart.heart.rejected." + id(); }

	/** The English text (the fallback the server sends; pinned against en_us.json by LangCoverageTest). */
	public String message() {
		return switch (this) {
			case NOT_OPEN -> "That screen is no longer open.";
			case STALE -> "The village changed. Open the Heart again.";
			case NO_VILLAGE -> "There is no village here.";
			case NO_PERMISSION -> "You are not allowed to do that.";
			case RATE_LIMITED -> "Slow down.";
			case UNKNOWN_JOB -> "That job does not exist.";
			case VILLAGER_NOT_FOUND -> "That villager is not here.";
			case NOT_A_BUILDING -> "That is not a building of this village.";
			case BAD_RANK -> "That rank cannot be given.";
			case UNKNOWN_PLAYER -> "This village has not seen that player.";
			case OWNER_RANK_FIXED -> "The Owner's rank cannot be changed that way.";
			case TOO_MANY_MEMBERS -> "The village cannot rank any more players.";
			case NOT_A_MEMBER -> "Make them a Resident or Steward first.";
			case TARGET_CHANGED -> "That player's rank just changed. Check and try again.";
			case BAD_AMOUNT -> "That amount cannot be withdrawn.";
			case BAD_SHIFT -> "That shift does not exist.";
			case NOT_A_GUARD -> "That villager is not a guard.";
		};
	}
}
