package dev.villageheart.core.blueprint;

/** What a build is doing, for the Buildings tab and the Builder's Table. */
public enum BuildStatus {
	WAITING_FOR_BUILDER, CLEARING, BUILDING, CHECKING, NEEDS_MATERIALS, BLOCKED, IN_THE_WAY, UNREACHABLE, NOT_LOADED;

	public String langSuffix() { return name().toLowerCase(java.util.Locale.ROOT); }
}
