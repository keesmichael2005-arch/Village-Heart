package dev.villageheart.gametest;

import dev.villageheart.needs.NeedsOverride;

/**
 * The needs and dinner switches of the GameTests (M11 plan 3.2 #12). {@link VillageTestSupport#reset} calls {@link #disable}: default needs
 * (no hunger, housed, paid) and no dinner, so the worker tests of M7-M10 keep their pace. A needs_ test calls {@link #enable} first.
 */
public final class NeedsTestHooks {
	private NeedsTestHooks() {}

	public static void disable() {
		NeedsOverride.forceDefaultNeeds = true;
		NeedsOverride.dinnerEnabled = false;
	}

	public static void enable() {
		NeedsOverride.forceDefaultNeeds = false;
		NeedsOverride.dinnerEnabled = true;
	}
}
