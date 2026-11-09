package dev.villageheart.core.job;

import dev.villageheart.core.Balance;
import java.util.Locale;
import java.util.Optional;

/** Pure rules about tools. The glue decides what counts as a tool of a kind (item tags); this maps ids to tiers. */
public final class ToolRules {
	private ToolRules() {}

	/** {@code minecraft:stone_axe} gives STONE; the prefix must be a tier name and the rest a tool name. Anything else is empty. */
	public static Optional<ToolTier> tierOf(String itemId) {
		int colon = itemId.indexOf(':');
		String path = colon < 0 ? itemId : itemId.substring(colon + 1);
		for (ToolTier t : ToolTier.values()) {
			String prefix = tierPrefix(t) + "_";
			if (path.startsWith(prefix) && isToolName(path.substring(prefix.length()))) return Optional.of(t);
		}
		return Optional.empty();
	}

	private static String tierPrefix(ToolTier t) {
		return t == ToolTier.WOOD ? "wooden" : t == ToolTier.GOLD ? "golden" : t.name().toLowerCase(Locale.ROOT);
	}

	private static boolean isToolName(String s) {
		return s.equals("axe") || s.equals("pickaxe") || s.equals("hoe") || s.equals("shovel") || s.equals("sword") || s.equals("spear");
	}

	public static double multiplier(ToolTier t) { return Balance.TOOL_SPEED[t.ordinal()]; }
}
