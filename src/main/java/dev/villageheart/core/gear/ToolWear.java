package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;

/** The worn line (M19a plan 3.2 #3), shared with M19's smith. */
public final class ToolWear {
	private ToolWear() {}

	/**
	 * A tool is worn when {@code remaining < max(ceil(GEAR_MIN_DURABILITY x max), 2 x wearPerUnit, wearPerUnit x ceil(leadTicks / unitTicks))}.
	 * {@code leadTicks} is how long the old tool must still last after the replacement is ordered: 0 for a self-craft. A tool that cannot be
	 * damaged ({@code max == 0}) is never worn.
	 */
	public static boolean worn(int remaining, int max, int wearPerUnit, int unitTicks, int leadTicks) {
		if (max <= 0) return false;
		int share = (int) Math.ceil(Balance.GEAR_MIN_DURABILITY * max - 1e-9);
		int lead = wearPerUnit * (int) Math.ceil(Math.max(0, leadTicks) / (double) unitTicks);
		return remaining < Math.max(share, Math.max(2 * wearPerUnit, lead));
	}
}
