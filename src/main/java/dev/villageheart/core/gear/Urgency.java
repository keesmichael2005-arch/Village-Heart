package dev.villageheart.core.gear;

/** How urgent a demand is; order by {@link #rank()}, never by ordinal (M19 adds U2 and U3). */
public enum Urgency {
	U1_MISSING_REQUIRED(1), U4_WORN_REQUIRED(4), U5_HOE(5);

	private final int rank;

	Urgency(int rank) { this.rank = rank; }

	public int rank() { return rank; }
}
