package dev.villageheart.core.guard;

/**
 * The pure rules of a raid chase (M25 step 19, plan risk 2). Combat applies them; the tests do not need a world.
 * A guard follows a raider of the tracked raid past the claim. The walk to the only gate can increase the
 * straight-line distance, and a kill in that margin still belongs to the guard. A direct path that stops at the wall
 * walks to the gate closest to the raider. A raider that never closes on the Heart is brought to that gate by
 * {@link dev.villageheart.core.raid.RaiderRecall}, not by moving the guard.
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

	/**
	 * Where to walk when the direct path does not reach the raider: the gate closest to the raider, so the guard leaves
	 * through a door instead of standing at the wall. Null when the direct path reaches, there is no gate, or the guard
	 * is already within 2 blocks of that gate. {@code gates} is x, y, z triples.
	 */
	public static int[] gateWaypoint(int gx, int gy, int gz, int rx, int ry, int rz, int[] gates, boolean directReaches) {
		if (directReaches || gates == null || gates.length < 3) return null;
		int best = -1;
		long bestD = Long.MAX_VALUE;
		for (int i = 0; i + 2 < gates.length; i += 3) {
			long dx = (long) gates[i] - rx, dy = (long) gates[i + 1] - ry, dz = (long) gates[i + 2] - rz;
			long d = dx * dx + dy * dy + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		if (best < 0) return null;
		long dx = (long) gates[best] - gx, dy = (long) gates[best + 1] - gy, dz = (long) gates[best + 2] - gz;
		if (dx * dx + dy * dy + dz * dz <= 4) return null;
		return new int[] { gates[best], gates[best + 1], gates[best + 2] };
	}

	/**
	 * Follow the partial path when it closes at least 12 blocks. A path that stops at a wall does not, and the guard
	 * leaves through a gate instead, but only when the raider is already within 32 blocks. A farther raider is brought
	 * to the gate by recall. Walking out to it is how the guards died on the ledge.
	 */
	public static boolean useGate(boolean pathReaches, double guardToTarget, double pathEndToTarget) {
		if (pathReaches || guardToTarget > 32) return false;
		return guardToTarget - pathEndToTarget < 12;
	}

}
