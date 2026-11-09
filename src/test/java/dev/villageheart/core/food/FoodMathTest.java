package dev.villageheart.core.food;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FoodMathTest {
	@Test void daysLeft() {
		assertEquals(10.0, FoodMath.daysLeft(400, 4, 0).getAsDouble(), 1e-9);
		assertEquals(8.0, FoodMath.daysLeft(400, 4, 2).getAsDouble(), 1e-9);
		assertTrue(FoodMath.daysLeft(400, 0, 0).isEmpty());
		assertEquals(0.0, FoodMath.daysLeft(0, 3, 1).getAsDouble(), 1e-9);
	}

	@Test void nutritionCountsRawButNotNeverEat() {
		List<FoodStock<String>> l = List.of(
				new FoodStock<>("bread", "minecraft:bread", new FoodValue(5, 6f, false), 10),
				new FoodStock<>("beef", "minecraft:beef", new FoodValue(3, 1.8f, true), 4),
				new FoodStock<>("flesh", "minecraft:rotten_flesh", new FoodValue(4, 0.8f, false), 100));
		assertEquals(62, FoodMath.nutrition(l));
	}
}
