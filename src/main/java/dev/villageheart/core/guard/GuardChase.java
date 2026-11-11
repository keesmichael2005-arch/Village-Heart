package dev.villageheart.core.guard;

/**
 * The two pure rules of a raid chase (M25 step 19, plan risk 2). Combat applies them; the tests do not need a world.
 * A guard follows a raider of the tracked raid 16 blocks past the claim. The walk to the only gate can increase the
 * straight-line distance, and a kill in that margin still belongs to the guard.
 */
public final class GuardChase {
	private GuardChase() {}

	/** A moving chase with a path still in progress is closing, even when the straight-line distance grew. */
	public static boolean detourCounts(boolean chase, boolean navigating, boolean pathInProgress, boolean cellChanged) {
		return chase && navigating && pathInProgress && cellChanged;
	}

	/** Inside the claim, or a raider still inside the chase margin. */
	public static boolean killCounts(boolean inClaim, boolean raiderWithinChase) {
		return inClaim || raiderWithinChase;
	}
}
