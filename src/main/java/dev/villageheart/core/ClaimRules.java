package dev.villageheart.core;

/**
 * Pure game rules for claiming a village. No Minecraft classes here on purpose:
 * everything in dev.villageheart.core is unit-testable in milliseconds.
 */
public final class ClaimRules {
	/** How far (in blocks) from a village bell the Village Heart may be placed. */
	public static final int MAX_BELL_DISTANCE = 16;

	public enum Result { CLAIMED, NO_BELL_NEARBY, ALREADY_HAVE_VILLAGE }

	private ClaimRules() {}

	/**
	 * @param distanceToBell distance to the nearest bell, or -1 if none was found
	 * @param worldAlreadyHasClaim whether this world already has a claimed village
	 */
	public static Result evaluate(double distanceToBell, boolean worldAlreadyHasClaim) {
		if (worldAlreadyHasClaim) return Result.ALREADY_HAVE_VILLAGE;
		if (distanceToBell < 0 || distanceToBell > MAX_BELL_DISTANCE) return Result.NO_BELL_NEARBY;
		return Result.CLAIMED;
	}
}
