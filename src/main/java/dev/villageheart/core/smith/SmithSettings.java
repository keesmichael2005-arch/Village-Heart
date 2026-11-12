package dev.villageheart.core.smith;

import dev.villageheart.core.Balance;

/** Tunables the gear planner reads. Only the diamond reserve for now; the rest live on {@link Balance}. */
public record SmithSettings(int diamondReserve) {
	public SmithSettings {
		if (diamondReserve < 0) throw new IllegalArgumentException("diamondReserve < 0");
	}

	public static SmithSettings defaults() { return new SmithSettings(Balance.SMITH_DIAMOND_RESERVE); }
}
