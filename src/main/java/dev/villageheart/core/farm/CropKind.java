package dev.villageheart.core.farm;

import java.util.Optional;

/**
 * The four crops a farmer works (DESIGN 5.2). Matched by block id, never by "is a CropBlock": the torchflower crop extends CropBlock too.
 * Wheat, carrots and potatoes ripen at age 7, beetroots at 3.
 */
public enum CropKind {
	WHEAT("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:wheat", 7),
	CARROTS("minecraft:carrots", "minecraft:carrot", "minecraft:carrot", 7),
	POTATOES("minecraft:potatoes", "minecraft:potato", "minecraft:potato", 7),
	BEETROOTS("minecraft:beetroots", "minecraft:beetroot_seeds", "minecraft:beetroot", 3);

	private final String blockId;
	private final String seedId;
	private final String productId;
	private final int maxAge;

	CropKind(String blockId, String seedId, String productId, int maxAge) {
		this.blockId = blockId;
		this.seedId = seedId;
		this.productId = productId;
		this.maxAge = maxAge;
	}

	public String blockId() { return blockId; }
	/** The item that is planted again. */
	public String seedId() { return seedId; }
	public String productId() { return productId; }
	public int maxAge() { return maxAge; }

	public boolean isMature(int age) { return age >= maxAge; }

	public static Optional<CropKind> byBlockId(String id) {
		for (CropKind k : values()) if (k.blockId.equals(id)) return Optional.of(k);
		return Optional.empty();
	}

	public static Optional<CropKind> bySeedId(String id) {
		for (CropKind k : values()) if (k.seedId.equals(id)) return Optional.of(k);
		return Optional.empty();
	}
}
