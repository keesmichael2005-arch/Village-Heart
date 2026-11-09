package dev.villageheart.core.guard;

/** What a guard remembers across a reload (M18 3.3): whether it is wounded (the {@link WoundRule} bit). */
public record GuardData(boolean wounded) {
	public static final GuardData FRESH = new GuardData(false);
}
