package dev.villageheart.core.heart;

/** Caps on what one snapshot holds, so its encoded size stays bounded (see HeartCodecsTest.worstCaseSize). */
public final class HeartLimits {
	private HeartLimits() {}

	public static final int MAX_VILLAGERS = 100;
	public static final int MAX_BUILDINGS = 128;
	/** Rows of the Ranks tab. */
	public static final int MAX_MEMBERS = 100;
	public static final int MAX_REASONS = 4;
	/** Builds shown on the Buildings tab and the Builder's Table. Placing a site is not capped. V draws the rest. */
	public static final int MAX_BUILDS = 8;
	public static final int MAX_BUILD_MISSING = 2;
	public static final int MAX_STOCK_KINDS = 256;
	public static final int MAX_JOB_CHOICES = 9;
	public static final int MAX_ALERTS = 5;
	/** Newest deaths drawn under the alerts on the Overview (M25). */
	public static final int MAX_DEATH_ROWS = 3;
	public static final int MAX_GOALS = 8;
	/** Goal rows the Overview screen draws (a tier has at most this many goals; pinned against {@code TierGoals.maxGoals()}). */
	public static final int MAX_GOAL_ROWS = 6;
	/** Guide task ids and need ids on one snapshot. The book is shorter than this; the spare is for tasks added later. */
	public static final int MAX_GUIDE = 64;
	public static final int MAX_TEXT = 120;
	public static final int MAX_NAME = 32;
	public static final int MAX_KEY = 64;
	public static final int MAX_ID = 64;
	/** Format args of a {@link TextLine}: counts and short names only (kept small so 512 reason lines stay under the size cap). */
	public static final int MAX_TEXT_ARGS = 2;
	public static final int MAX_ARG = 16;
	/** Format args of the build texts (M20): a position such as "(-30000000, 319, -30000000)" is 27 characters. Only lines whose key starts with "build.villageheart.". */
	public static final int MAX_BUILD_ARG = 32;
	/** Longest job id a client may send. */
	public static final int MAX_JOB_ID = 32;

	/** {@code s} cut to at most {@code max} chars. */
	public static String cut(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max);
	}
}
