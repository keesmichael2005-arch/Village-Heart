package dev.villageheart.tier;

/** Runtime counters (tests and logs). */
public final class TierStats {
	private TierStats() {}

	/** Times a dinner reset a running fed streak since the server started. */
	public static int streakResets;
	public static int tierUps;
}
