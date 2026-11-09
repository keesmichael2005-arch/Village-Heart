package dev.villageheart.worker.gear;

/**
 * Whether workers make their own tools (M19a). On by default; only GameTests turn it off for tests where a tool worker with no or a worn tool
 * would otherwise stop to craft (an older test whose subject is not self-crafting). {@code VillageTestSupport.reset} puts it back.
 */
public final class SelfCraftSwitch {
	private static volatile boolean enabled = true;

	private SelfCraftSwitch() {}

	public static boolean enabled() { return enabled; }

	/** Tests only: no worker starts or continues a self-craft until {@link #reset()}. */
	public static void off() { enabled = false; }

	public static void reset() { enabled = true; }
}
