package dev.villageheart.core;

/**
 * Player ranks in a village, from most to least power. The order is load-bearing: {@link #atLeast}, the
 * Heart snapshot codec (ordinals) and the SetRank payload depend on it. A test pins it.
 */
public enum Rank {
	OWNER, STEWARD, RESIDENT, VISITOR;

	/** True when this rank is {@code min} or more powerful. */
	public boolean atLeast(Rank min) { return ordinal() <= min.ordinal(); }
}
