package dev.villageheart.core.farm;

import dev.villageheart.core.Balance;

/**
 * What a farmer never hands in to Storage (M10 plan 3.2 #9): the seeds it replants with and composts (one stack of each seed at most, so
 * seeds can never fill the 8-slot inventory), a reserve of carrots and potatoes to replant, and bone meal. Everything else is handed in.
 */
public final class FarmKeep {
	private FarmKeep() {}

	public static int keep(String itemId, int have) {
		return switch (itemId) {
			case "minecraft:wheat_seeds", "minecraft:beetroot_seeds" -> Math.min(have, Balance.SEED_KEEP_MAX);
			case "minecraft:carrot", "minecraft:potato" -> Math.min(have, Balance.SEED_RESERVE);
			case "minecraft:bone_meal" -> Math.min(have, 64);
			default -> 0;
		};
	}

	/** True for the two seeds the farmer composts. */
	public static boolean isCompostSeed(String itemId) { return itemId.equals("minecraft:wheat_seeds") || itemId.equals("minecraft:beetroot_seeds"); }

	/** Seeds above the reserve: what the farmer composts. */
	public static int surplus(int have) { return Math.max(0, have - Balance.SEED_RESERVE); }
}
