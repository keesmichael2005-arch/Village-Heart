package dev.villageheart.core.heart;

import java.util.Objects;

/**
 * @param nutritionEach 0 when the item is not food, or not food the village eats
 * @param forSale       how many the market may sell now (M22): the part above the reserves; 0 for an item it does not buy
 */
public record StockLine(String itemId, long count, int nutritionEach, long forSale) {
	public StockLine {
		Objects.requireNonNull(itemId, "itemId");
		forSale = Math.max(0, forSale); // a damaged or hostile packet cannot make the client throw
	}

	/** Without the market's figure (the shape before M22). */
	public StockLine(String itemId, long count, int nutritionEach) { this(itemId, count, nutritionEach, 0); }
}
