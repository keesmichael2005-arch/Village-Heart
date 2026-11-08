package dev.villageheart.core.mine;

import dev.villageheart.core.Balance;

/**
 * Underground work progress of one away worker, in units ({@link Balance#MINER_HAUL_UNITS} per haul). Saved with the worker; the
 * fractional remainder carries to the next haul (the same rule as {@code WorkClock}, which is transient and per tick).
 */
public record AwayProgress(double units) {
	public static final AwayProgress ZERO = new AwayProgress(0);
	private static final double EPS = 1e-9;

	public AwayProgress {
		if (!(units >= 0)) throw new IllegalArgumentException("units must be >= 0: " + units);
	}

	/** The result of one step: the new progress and the hauls completed (0 or 1). */
	public record Step(AwayProgress next, int hauls) {}

	/** At most one haul per step; what is left over is capped at one haul. */
	public Step add(int ticks, double multiplier) { return add(ticks, multiplier, Balance.MINER_HAUL_UNITS); }

	/** The same for a job whose haul or trip costs {@code unitCost} units (the hunter, M17). */
	public Step add(int ticks, double multiplier, double unitCost) {
		double u = units + (multiplier > 0 ? ticks * multiplier : 0);
		if (u >= unitCost - EPS) {
			double rest = Math.min(Math.max(u - unitCost, 0), unitCost);
			return new Step(new AwayProgress(rest), 1);
		}
		return new Step(new AwayProgress(u), 0);
	}

	/** The same, but a haul that falls due is not taken: the progress stays at one haul (the shaft depth is not known yet). */
	public AwayProgress addHeld(int ticks, double multiplier) { return addHeld(ticks, multiplier, Balance.MINER_HAUL_UNITS); }

	public AwayProgress addHeld(int ticks, double multiplier, double unitCost) {
		double u = units + (multiplier > 0 ? ticks * multiplier : 0);
		return new AwayProgress(Math.min(u, unitCost));
	}

	/** A whole haul is waiting. */
	public boolean due() { return due(Balance.MINER_HAUL_UNITS); }

	public boolean due(double unitCost) { return units >= unitCost - EPS; }
}
