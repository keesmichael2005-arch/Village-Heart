package dev.villageheart.core.gear;

/** How urgent a demand is. Order by {@link #rank()}, never by ordinal: U2 and U3 are appended after U5. */
public enum Urgency {
	U1_MISSING_REQUIRED(1), U4_WORN_REQUIRED(4), U5_HOE(5), U2_GUARD_WEAPON(2), U3_GUARD_ARMOUR(3);

	private final int rank;

	Urgency(int rank) { this.rank = rank; }

	public int rank() { return rank; }
}
