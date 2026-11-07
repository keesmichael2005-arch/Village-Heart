package dev.villageheart.core;

/** The one place for tunable numbers (DESIGN section 12). */
public final class Balance {
	private Balance() {}

	/** Blocks around the Heart that count as the village (DESIGN section 1, tunable). */
	public static final int CLAIM_RADIUS = 80;

	/** Blank Markers crafted from 8 planks around an item frame (DESIGN section 3.1, tunable). */
	public static final int BLANK_MARKER_YIELD = 1;
}
