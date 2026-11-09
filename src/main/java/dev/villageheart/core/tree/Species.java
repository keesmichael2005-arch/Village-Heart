package dev.villageheart.core.tree;

/** Tree species the lumberjack knows. A sapling of the species stands where the trunk stood. */
public enum Species {
	OAK("minecraft:oak_sapling", false),
	SPRUCE("minecraft:spruce_sapling", false),
	BIRCH("minecraft:birch_sapling", false),
	JUNGLE("minecraft:jungle_sapling", false),
	ACACIA("minecraft:acacia_sapling", false),
	DARK_OAK("minecraft:dark_oak_sapling", true),
	PALE_OAK("minecraft:pale_oak_sapling", true),
	MANGROVE("minecraft:mangrove_propagule", false),
	CHERRY("minecraft:cherry_sapling", false),
	/** Oak logs under azalea leaves. */
	AZALEA("minecraft:azalea", false);

	private final String saplingId;
	private final boolean twoByTwo;

	Species(String saplingId, boolean twoByTwo) {
		this.saplingId = saplingId;
		this.twoByTwo = twoByTwo;
	}

	public String saplingId() { return saplingId; }

	/** Dark oak and pale oak only grow from a 2x2 of saplings. */
	public boolean needsTwoByTwo() { return twoByTwo; }
}
