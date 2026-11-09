package dev.villageheart.core.worker;

/**
 * The next stop on the way back to a job block. A pathfinder gives up on a goal that is further than its range (48 blocks), so an idle worker
 * that drifted that far (vanilla idle strolls wander) would never find its way back. It walks in legs of at most {@link #LEG} blocks instead.
 * Pure geometry: the caller puts the point on the ground.
 */
public final class ReturnLeg {
	private ReturnLeg() {}

	/** Longest leg; well inside the walk's path range. */
	public static final double LEG = 24.0;

	/** @return {x, z} of the point {@code min(LEG, distance)} blocks from (px, pz) towards (tx, tz); the target itself when it is within one leg */
	public static double[] next(double px, double pz, double tx, double tz) {
		double dx = tx - px, dz = tz - pz, d = Math.sqrt(dx * dx + dz * dz);
		if (d <= LEG) return new double[] {tx, tz};
		return new double[] {px + dx * LEG / d, pz + dz * LEG / d};
	}
}
