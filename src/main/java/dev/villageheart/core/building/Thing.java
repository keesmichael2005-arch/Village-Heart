package dev.villageheart.core.building;

import java.util.Locale;

/** Something a building can be short of; {@link #phrase(int)} is the noun in "Needs 2 more chests". */
public enum Thing {
	CHEST("chest", "chests"),
	/** Storage counts chests and barrels but still reads "chest" (the roadmap text). */
	STORAGE_CONTAINER("chest", "chests"),
	BED,
	LIGHT("light source", "light sources"),
	COMPOSTER,
	CHOPPING_BLOCK,
	SMOKER,
	CRAFTING_TABLE,
	BUILDERS_TABLE("builder's table", "builder's tables"),
	STONECUTTER,
	FLETCHING_TABLE,
	GRINDSTONE,
	MARKET_STALL,
	BLAST_FURNACE,
	ANVIL,
	RAIL,
	NATURAL("grass, leaf or log block", "grass, leaf or log blocks"),
	DUMMY("armor stand or target", "armor stands or targets");

	private final String one;
	private final String many;

	Thing() {
		this.one = name().toLowerCase(Locale.ROOT).replace('_', ' ');
		this.many = one + "s";
	}

	Thing(String one, String many) {
		this.one = one;
		this.many = many;
	}

	public String phrase(int n) { return n == 1 ? one : many; }
}
