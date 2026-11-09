package dev.villageheart.core.food;

import dev.villageheart.core.Balance;
import java.util.Comparator;

/** Which food the village eats and in which order (DESIGN section 6: best food first, cooked before raw). */
public final class FoodRules {
	private FoodRules() {}

	public static boolean isRaw(String itemId) { return Balance.FOOD_RAW.contains(itemId); }

	public static boolean neverEat(String itemId) { return Balance.FOOD_NEVER_EAT.contains(itemId); }

	/** Prepared before raw, then nutrition descending, then saturation descending, then item id ascending (deterministic). */
	public static <K> Comparator<FoodStock<K>> bestFirst() {
		return Comparator.<FoodStock<K>, Boolean>comparing(s -> s.value().raw())
				.thenComparing(Comparator.<FoodStock<K>>comparingInt(s -> s.value().nutrition()).reversed())
				.thenComparing(Comparator.<FoodStock<K>>comparingDouble(s -> s.value().saturation()).reversed())
				.thenComparing(FoodStock::id);
	}
}
