package dev.villageheart.core.market;

/**
 * One kind of stack in the stockpile, as the market sees it. {@code plain} = no component changes (a renamed or enchanted item is the player's);
 * {@code nutritionEach} is 0 unless the village eats it; {@code reservedByOthers} is what builds and cooks have claimed.
 */
public record StockItem<K>(K key, String id, boolean plain, long count, long reservedByOthers, int nutritionEach) {
	public StockItem {
		if (count < 0 || reservedByOthers < 0 || nutritionEach < 0) throw new IllegalArgumentException("negative stock: " + count + ", " + reservedByOthers + ", " + nutritionEach);
	}

	/** Items nobody has claimed. */
	public long free() { return Math.max(0, count - reservedByOthers); }
}
