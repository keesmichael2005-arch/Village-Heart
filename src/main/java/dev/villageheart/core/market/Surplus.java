package dev.villageheart.core.market;

import java.util.List;

/**
 * What the market may sell (M22 plan 3.2 #2). Non-food: the count above the claims of others and the item reserve. Food: the village keeps
 * {@code foodDays} days of nutrition; everything above that is for sale, whichever food it is. Saturating long arithmetic.
 */
public final class Surplus {
	private Surplus() {}

	/** Nutrition of all food in the stock, claims of others left out (the number the Overview's food days use). */
	public static <K> long foodPool(List<StockItem<K>> stock) {
		long sum = 0;
		for (StockItem<K> s : stock) {
			if (s.nutritionEach() <= 0) continue;
			sum = saturatingAdd(sum, saturatingMul(s.nutritionEach(), s.free()));
		}
		return sum;
	}

	/** Nutrition above the food reserve; {@code demand} is the village's daily need (adults x 10 + children x 5). */
	public static <K> long spareNutrition(List<StockItem<K>> stock, Reserves r, long demand) {
		long reserve = demand <= 0 ? 0 : saturatingMul(r.foodDays(), demand);
		return Math.max(0, foodPool(stock) - reserve);
	}

	/** Items of {@code item} that may be sold now: 0 for an item that is not priced or not plain. */
	public static <K> long of(StockItem<K> item, List<StockItem<K>> stock, Reserves r, long demand) {
		if (!item.plain() || PriceTable.lot(item.id()).isEmpty()) return 0;
		if (item.nutritionEach() > 0) return Math.min(item.free(), spareNutrition(stock, r, demand) / item.nutritionEach());
		return Math.max(0, item.free() - r.itemReserve());
	}

	private static long saturatingAdd(long a, long b) {
		long s = a + b;
		return s < 0 ? Long.MAX_VALUE : s;
	}

	private static long saturatingMul(long a, long b) {
		if (a == 0 || b == 0) return 0;
		return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
	}
}
