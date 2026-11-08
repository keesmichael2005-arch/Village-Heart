package dev.villageheart.core.cook;

/** {@code count} of one item (an id such as {@code minecraft:beef}). Shared with the Blacksmith's recipes (M19). */
public record Ingredient(String itemId, int count) {
	public Ingredient {
		if (itemId == null || itemId.isEmpty()) throw new IllegalArgumentException("itemId");
		if (count < 1) throw new IllegalArgumentException("count must be >= 1: " + count);
	}
}
