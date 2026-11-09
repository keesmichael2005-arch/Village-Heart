package dev.villageheart.core.economy;

import dev.villageheart.core.Balance;

/** The clamps of deposits and withdrawals. */
public final class TreasuryMath {
	private TreasuryMath() {}

	/** How much of {@code offered} fits under {@link Balance#MAX_TREASURY}. */
	public static int depositable(int treasury, int offered) { return Math.max(0, Math.min(offered, Balance.MAX_TREASURY - treasury)); }

	/** How much of {@code asked} the treasury holds. */
	public static int withdrawable(int treasury, int asked) { return Math.max(0, Math.min(asked, treasury)); }
}
