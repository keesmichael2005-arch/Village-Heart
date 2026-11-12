package dev.villageheart.core.gear;

import dev.villageheart.core.job.ToolTier;
import java.util.Optional;

/**
 * What an item is for the gear census, including gold, chainmail and netherite, which the smith never makes.
 * Rank matches {@link CraftTier#rank()} where the tiers overlap, and extends it: gold and chainmail share copper's 3, netherite is 6.
 */
public record GearInfo(GearNeed need, int rank, boolean ironOrBetter) {
	public GearInfo {
		if (rank < 1) throw new IllegalArgumentException("rank < 1");
	}

	/** The rank scale of a tool tier (a golden tool is 3, the same as copper). */
	public static int rankOf(ToolTier tier) {
		return switch (tier) {
			case WOOD -> 1;
			case STONE -> 2;
			case COPPER, GOLD -> 3;
			case IRON -> 4;
			case DIAMOND -> 5;
			case NETHERITE -> 6;
		};
	}

	/**
	 * A tool or armour piece the village understands. Shovels, spears, pumpkins, elytra and anything else are empty.
	 * {@code ironOrBetter} is iron, diamond and netherite; gold is not.
	 */
	public static Optional<GearInfo> of(String id) {
		if (id == null || !id.startsWith("minecraft:")) return Optional.empty();
		String path = id.substring("minecraft:".length());
		GearNeed need = needOf(path);
		if (need == null) return Optional.empty();
		int rank = rankOf(path);
		if (rank == 0) return Optional.empty();
		return Optional.of(new GearInfo(need, rank, rank >= 4));
	}

	private static GearNeed needOf(String path) {
		if (path.endsWith("_pickaxe")) return GearNeed.PICKAXE;
		if (path.endsWith("_axe")) return GearNeed.AXE;
		if (path.endsWith("_hoe")) return GearNeed.HOE;
		if (path.endsWith("_sword")) return GearNeed.SWORD;
		if (path.endsWith("_helmet")) return GearNeed.HEAD;
		if (path.endsWith("_chestplate")) return GearNeed.CHEST;
		if (path.endsWith("_leggings")) return GearNeed.LEGS;
		if (path.endsWith("_boots")) return GearNeed.FEET;
		return null;
	}

	private static int rankOf(String path) {
		if (path.startsWith("wooden_") || path.startsWith("leather_")) return 1;
		if (path.startsWith("stone_")) return 2;
		if (path.startsWith("golden_") || path.startsWith("copper_") || path.startsWith("chainmail_")) return 3;
		if (path.startsWith("iron_")) return 4;
		if (path.startsWith("diamond_")) return 5;
		if (path.startsWith("netherite_")) return 6;
		return 0;
	}
}
