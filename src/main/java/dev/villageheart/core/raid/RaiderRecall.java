package dev.villageheart.core.raid;

import dev.villageheart.core.Balance;

/**
 * A raider that never walks toward the Heart holds its wave until the raid stops (M25 step 19).
 * It is placed just outside the nearest gate, not inside the ring, and the guard is not moved onto it.
 */
public final class RaiderRecall {
	private RaiderRecall() {}

	/**
	 * Stalled, still far from a gate, and the door is not already crowded. Two raiders at the gate are enough; a third
	 * dropped on that fight is what killed the guards.
	 */
	public static boolean recall(boolean alive, long ticksWithoutClosing, double distanceToGate, int raidersNearGate) {
		return alive && ticksWithoutClosing >= Balance.RAIDER_RECALL_STALL && distanceToGate > Balance.RAIDER_RECALL_NEAR_GATE
				&& raidersNearGate < Balance.RAIDER_RECALL_AT_GATE;
	}

	/** The gate closest to the raider. {@code gates} is x, y, z triples. Null when there is no gate. */
	public static int[] nearestGate(int rx, int ry, int rz, int[] gates) {
		if (gates == null || gates.length < 3) return null;
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
		return new int[] { gates[best], gates[best + 1], gates[best + 2] };
	}

	/** Four blocks outside the gate, on the side away from the Heart, so the raider is not dropped in the yard. */
	public static double[] outsideGate(int gx, int gy, int gz, int hx, int hz) {
		double dx = gx - hx, dz = gz - hz;
		double h = Math.max(Math.sqrt(dx * dx + dz * dz), 0.1);
		double step = Balance.RAIDER_RECALL_OUTSIDE;
		return new double[] { gx + dx / h * step, gy, gz + dz / h * step };
	}
}
