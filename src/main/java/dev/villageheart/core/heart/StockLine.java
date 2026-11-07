package dev.villageheart.core.heart;

import java.util.Objects;

/** @param nutritionEach 0 when the item is not food, or not food the village eats */
public record StockLine(String itemId, long count, int nutritionEach) {
	public StockLine { Objects.requireNonNull(itemId, "itemId"); }
}
