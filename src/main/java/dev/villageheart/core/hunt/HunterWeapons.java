package dev.villageheart.core.hunt;

import dev.villageheart.core.job.ToolTier;

/** Which weapon the hunter takes from the Lodge chest: any bow or crossbow before any sword, swords by tier. */
public final class HunterWeapons {
	private HunterWeapons() {}

	/** Above every {@link ToolTier} ordinal. */
	public static final int RANGED_RANK = 100;

	public static int rank(boolean ranged, ToolTier tier) { return ranged ? RANGED_RANK : tier.ordinal(); }
}
