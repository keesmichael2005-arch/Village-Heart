package dev.villageheart.core;

/** Village size tier. Progression arrives in M14. */
public enum Tier {
	HAMLET("Hamlet"), VILLAGE("Village"), TOWN("Town");

	private final String displayName;

	Tier(String displayName) { this.displayName = displayName; }

	public String displayName() { return displayName; }
}
