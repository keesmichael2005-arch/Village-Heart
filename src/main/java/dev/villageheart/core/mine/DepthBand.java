package dev.villageheart.core.mine;

import dev.villageheart.core.Balance;

/** The depth band of a Mineshaft's deepest point (DESIGN section 5.3): the boundaries 40 and 0 belong to the upper band. */
public enum DepthBand {
	HIGH, MID, DEEP;

	public static DepthBand of(int deepestY) {
		if (deepestY >= Balance.BAND_HIGH_MIN_Y) return HIGH;
		if (deepestY >= Balance.BAND_MID_MIN_Y) return MID;
		return DEEP;
	}
}
