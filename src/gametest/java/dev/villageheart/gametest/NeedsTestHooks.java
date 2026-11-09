package dev.villageheart.gametest;

import dev.villageheart.needs.NeedsOverride;

/**
 * The needs and dinner switches of the GameTests (M11 plan 3.2 #12). {@link VillageTestSupport#reset} calls {@link #disable}: default needs
 * (no hunger, housed) and no dinner, and no wages, so the worker tests of M7-M10 keep their pace. A needs_ test calls {@link #enable} first.
 */
public final class NeedsTestHooks {
	private NeedsTestHooks() {}

	public static void disable() {
		NeedsOverride.forceDefaultNeeds = true;
		NeedsOverride.dinnerEnabled = false;
		NeedsOverride.wagesEnabled = false; // M12: no older test ever strikes or loses treasury; only the wages_ tests switch it back on
	}

	public static void enable() {
		NeedsOverride.forceDefaultNeeds = false;
		NeedsOverride.dinnerEnabled = true;
	}

	/** Wages on (M12): hires are charged and every day change runs the wage system. Fed and housed stay as {@link #disable} left them. */
	public static void enableWages() {
		NeedsOverride.wagesEnabled = true;
	}
}
