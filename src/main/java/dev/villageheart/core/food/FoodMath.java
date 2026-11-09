package dev.villageheart.core.food;

import dev.villageheart.core.Balance;
import java.util.List;
import java.util.OptionalDouble;

public final class FoodMath {
	private FoodMath() {}

	/** Nutrition of everything in the stock list (raw included; the caller leaves out what is never eaten). */
	public static <K> long nutrition(List<FoodStock<K>> stock) {
		long sum = 0;
		for (FoodStock<K> s : stock) if (!FoodRules.neverEat(s.id())) sum += Math.max(0, s.value().nutrition()) * Math.max(0, s.available());
		return sum;
	}

	/** Days the nutrition lasts: it divided by the real daily need (adults 10, children 5). Empty when nobody lives here. */
	public static OptionalDouble daysLeft(long nutrition, int adults, int children) {
		long demand = (long) adults * Balance.ADULT_NUTRITION + (long) children * Balance.CHILD_NUTRITION;
		if (demand <= 0) return OptionalDouble.empty();
		return OptionalDouble.of((double) nutrition / demand);
	}
}
