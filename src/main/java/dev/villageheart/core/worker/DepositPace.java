package dev.villageheart.core.worker;

import dev.villageheart.core.Balance;

/** How long a worker stands at a chest while the goods go in: one item per {@link Balance#DEPOSIT_TICKS_PER_ITEM}. */
public final class DepositPace {
	private DepositPace() {}

	public static int ticks(int items) {
		if (items <= 0) return 0;
		return items * Balance.DEPOSIT_TICKS_PER_ITEM;
	}
}
