package dev.villageheart.core;

/** Village size tier. A village grows one step per dawn when the next tier's goals are met (core.tier). */
public enum Tier {
	HAMLET("Hamlet"), VILLAGE("Village"), TOWN("Town");

	private final String displayName;

	Tier(String displayName) { this.displayName = displayName; }

	public String displayName() { return displayName; }
}
