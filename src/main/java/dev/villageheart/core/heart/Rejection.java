package dev.villageheart.core.heart;

import java.util.Locale;

/** Why the server refused a Heart screen action. */
public enum Rejection {
	NOT_OPEN, STALE, NO_VILLAGE, NO_PERMISSION, RATE_LIMITED, UNKNOWN_JOB, VILLAGER_NOT_FOUND, NOT_A_BUILDING;

	public String id() { return name().toLowerCase(Locale.ROOT); }

	/** Lang key of the reply text. */
	public String langKey() { return "message.villageheart.heart.rejected." + id(); }
}
