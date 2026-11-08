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
	public int selfTicks() { return Balance.SELF_TOOL_TICKS[ordinal()]; }

	public String id() { return name().toLowerCase(Locale.ROOT); }
}
