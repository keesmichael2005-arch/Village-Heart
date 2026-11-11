package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;

/** Wounded with hysteresis (M18 3.3): becomes true below 30 % of max health, false again at or above 80 %. */
public final class WoundRule {
	private WoundRule() {}

	public static boolean wounded(boolean previous, double health, double max) {
		if (max <= 0) return false;
		double f = health / max;
		return previous ? f < Balance.GUARD_HEALED_AT : f < Balance.GUARD_WOUNDED_BELOW;
	}
}
