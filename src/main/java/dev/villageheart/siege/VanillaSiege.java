package dev.villageheart.siege;

/**
 * Test hooks for the vanilla {@code VillageSiege} mixin (docs/plans/M25.md 3.12). The suppression itself is not a
 * gamerule: a claim stays free of vanilla zombies even when {@code villageheart:sieges} is off.
 */
public final class VanillaSiege {
	private static boolean tonight;
	private static int suppressed;

	private VanillaSiege() {}

	/** The next night roll of {@code nextInt(10)} in {@code VillageSiege.tick} returns 0. */
	public static void forceTonight() { tonight = true; }

	public static boolean tonight() { return tonight; }

	public static void noteSuppressed() { suppressed++; }

	public static int suppressedCount() { return suppressed; }

	public static void clear() {
		tonight = false;
		suppressed = 0;
	}
}
