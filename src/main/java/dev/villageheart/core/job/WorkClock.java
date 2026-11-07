package dev.villageheart.core.job;

/**
 * Turns a per-tick speed multiplier into completed units of work (one log, one crop...). The fractional remainder carries over, so the
 * long-run rate is exact. One per running task; not saved.
 */
public final class WorkClock {
	private final double unitCost;
	private double acc;

	public WorkClock(double unitCost) {
		if (!(unitCost > 0)) throw new IllegalArgumentException("unitCost must be > 0: " + unitCost);
		this.unitCost = unitCost;
	}

	/** Units completed this tick (0 or more). */
	public int tick(double multiplier) {
		if (multiplier <= 0) return 0;
		acc += multiplier;
		int units = 0;
		while (acc >= unitCost - 1e-9) {
			acc -= unitCost;
			units++;
		}
		if (acc < 0) acc = 0;
		return units;
	}

	/** Progress of the current unit, 0 (inclusive) to 1 (exclusive). */
	public double progress() { return Math.min(Math.max(acc / unitCost, 0.0), Math.nextDown(1.0)); }

	public void reset() { acc = 0; }
}
