package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;
import java.util.Locale;

/** The tiers a worker can make itself. M19 adds LEATHER, COPPER, IRON, DIAMOND after these; save codecs store {@code name()}, never the ordinal. */
public enum CraftTier {
	WOODEN("wooden"), STONE("stone");

	private final String prefix;

	CraftTier(String prefix) { this.prefix = prefix; }

	/** "wooden" or "stone": the item id prefix ({@code minecraft:stone_axe}). */
	public String itemPrefix() { return prefix; }

	/** Work ticks at level 1 for a self-made tool of this tier. */
	public int selfTicks() {
		return switch (this) {
			case WOODEN -> Balance.SELF_WOODEN_TICKS;
			case STONE -> Balance.SELF_STONE_TICKS;
		};
	}

	/** Quality order for comparisons (WOODEN < STONE); M19 slots higher tiers above by rank, whatever their ordinal. */
	public int rank() {
		return switch (this) {
			case WOODEN -> 1;
			case STONE -> 2;
		};
	}

	public String id() { return name().toLowerCase(Locale.ROOT); }
}
