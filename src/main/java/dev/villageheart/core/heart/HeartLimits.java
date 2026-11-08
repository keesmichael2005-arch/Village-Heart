package dev.villageheart.core.heart;

/** Caps on what one snapshot holds, so its encoded size stays bounded (see HeartCodecsTest.worstCaseSize). */
public final class HeartLimits {
	private HeartLimits() {}

	public static final int MAX_VILLAGERS = 100;
	public static final int MAX_BUILDINGS = 128;
	/** Rows of the Ranks tab. */
	public static final int MAX_MEMBERS = 100;
	public static final int MAX_REASONS = 4;
	/** Builds shown on the Buildings tab (M20: one per active build, Balance.MAX_ACTIVE_BUILDS) and the missing-material lines of each. */
	public static final int MAX_BUILDS = 8;
	public static final int MAX_BUILD_MISSING = 2;
	public static final int MAX_STOCK_KINDS = 256;
	public static final int MAX_JOB_CHOICES = 9;
	public static final int MAX_ALERTS = 5;
	public static final int MAX_GOALS = 8;
	public static final int MAX_TEXT = 120;
	public static final int MAX_NAME = 32;
	public static final int MAX_KEY = 64;
	public static final int MAX_ID = 64;
	/** Format args of a {@link TextLine}: counts and short names only (kept small so 512 reason lines stay under the size cap). */
	public static final int MAX_TEXT_ARGS = 2;
	public static final int MAX_ARG = 16;
	/** Longest job id a client may send. */
	public static final int MAX_JOB_ID = 32;

	/** {@code s} cut to at most {@code max} chars. */
	public static String cut(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}
}
