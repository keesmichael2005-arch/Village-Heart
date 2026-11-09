package dev.villageheart.core.gear;

import java.util.Optional;
import java.util.Set;

/**
 * Which planks a log (or stem) is made into: {@code minecraft:oak_log} to {@code minecraft:oak_planks}. Pure; the L2 test T-L2-13 checks
 * each entry against the game's recipes. Bamboo blocks (2 planks each) are not used.
 */
public final class PlankTable {
	private static final Set<String> WOODS = Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
			"crimson", "warped");

	private PlankTable() {}

	/** The 11 woods with their planks ids. */
	public static Set<String> woods() { return WOODS; }

	/**
	 * {@code minecraft:stripped_oak_log}, {@code oak_wood}, {@code crimson_stem}, {@code warped_hyphae}: the planks they give, or empty for
	 * anything else.
	 */
	public static Optional<String> planksOf(String logId) {
		int colon = logId.indexOf(':');
		String ns = colon < 0 ? "minecraft" : logId.substring(0, colon);
		String path = colon < 0 ? logId : logId.substring(colon + 1);
		if (!ns.equals("minecraft")) return Optional.empty();
		if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
		for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae"}) {
			if (path.endsWith(suffix)) {
				String wood = path.substring(0, path.length() - suffix.length());
				boolean stem = suffix.equals("_stem") || suffix.equals("_hyphae");
				if (!WOODS.contains(wood)) return Optional.empty();
				if (stem != (wood.equals("crimson") || wood.equals("warped"))) return Optional.empty();
				return Optional.of("minecraft:" + wood + "_planks");
			}
		}
		return Optional.empty();
	}
}
