package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.food.FoodMath;
import dev.villageheart.core.food.FoodStock;
import dev.villageheart.core.food.FoodValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SurplusTest {
	private static final Reserves R = Reserves.DEFAULT;

	private static StockItem<String> item(String id, long count, long reserved, int nut) { return new StockItem<>(id, "minecraft:" + id, true, count, reserved, nut); }

	private static long of(StockItem<String> it, List<StockItem<String>> all, Reserves r, long demand) { return Surplus.of(it, all, r, demand); }

	private static long solo(String id, long count, long reserved) {
		StockItem<String> it = item(id, count, reserved, 0);
		return of(it, List.of(it), R, 0);
	}

	@Test
	void nonFood() {
		assertEquals(136, solo("cobblestone", 200, 0));
		assertEquals(0, solo("cobblestone", 64, 0));
		assertEquals(0, solo("cobblestone", 10, 0));
		assertEquals(36, solo("cobblestone", 200, 100));
		assertEquals(0, solo("cobblestone", 200, 150));
		StockItem<String> it = item("cobblestone", 200, 0, 0);
		assertEquals(200, of(it, List.of(it), new Reserves(3, 0), 0));
	}

	@Test
	void notPlainOrNotPricedIsZero() {
		StockItem<String> named = new StockItem<>("n", "minecraft:cobblestone", false, 500, 0, 0);
		assertEquals(0, of(named, List.of(named), R, 0));
		assertEquals(0, solo("stone", 500, 0));
	}

	@Test
	void foodKeepsDaysOfNutrition() {
		StockItem<String> beef = item("cooked_beef", 40, 0, 8);
		assertEquals(25, of(beef, List.of(beef), R, 40)); // pool 320, reserve 120, spare 200
		StockItem<String> bread = item("bread", 20, 0, 5);
		assertEquals(37, of(beef, List.of(beef, bread), R, 40)); // pool 420, spare 300
		assertEquals(0, of(bread, List.of(beef, bread), R, 40), "bread is not priced");
		assertEquals(40, of(beef, List.of(beef), R, 0), "nobody to feed");
		assertEquals(40, of(beef, List.of(beef), new Reserves(0, 64), 40));
		StockItem<String> ten = item("cooked_beef", 10, 0, 8);
		assertEquals(10, of(ten, List.of(ten), R, 0), "food is not held to the item reserve");
	}

	@Test
	void demandFromChildren() {
		StockItem<String> beef = item("cooked_beef", 40, 0, 8);
		assertEquals((320 - 60) / 8, of(beef, List.of(beef), R, 20)); // 1 adult + 2 babies = 20
	}

	@Test
	void reservationsLowerThePool() {
		StockItem<String> beef = item("cooked_beef", 40, 0, 8);
		StockItem<String> bread = item("bread", 20, 10, 5);
		assertEquals((320 + 50 - 120) / 8, of(beef, List.of(beef, bread), R, 40));
	}

	@Test
	void cropsAreFoodAndWheatIsNot() {
		StockItem<String> carrot = item("carrot", 100, 0, 3);
		assertEquals(Math.min(100, (300 - 120) / 3), of(carrot, List.of(carrot), R, 40));
		StockItem<String> wheat = item("wheat", 100, 0, 0);
		assertEquals(36, of(wheat, List.of(wheat), R, 40));
	}

	@Test
	void overflowSafe() {
		long big = Long.MAX_VALUE / 2;
		StockItem<String> beef = item("cooked_beef", big, 0, 8);
		assertEquals((Long.MAX_VALUE - 120) / 8, of(beef, List.of(beef), R, 40), "the pool saturates, the answer stays sane");
		StockItem<String> cobble = item("cobblestone", big, 0, 0);
		assertEquals(big - 64, of(cobble, List.of(cobble), R, 40));
	}

	@Test
	void agreesWithFoodMath() {
		Random rnd = new Random(22);
		String[] ids = {"cooked_beef", "bread", "carrot", "apple", "cooked_cod", "potato"};
		int[] nut = {8, 5, 3, 4, 5, 1};
		for (int n = 0; n < 500; n++) {
			List<StockItem<String>> stock = new ArrayList<>();
			List<FoodStock<String>> food = new ArrayList<>();
			for (int i = 0; i < ids.length; i++) {
				if (rnd.nextBoolean()) continue;
				long count = rnd.nextInt(300), res = rnd.nextInt(5) == 0 ? rnd.nextInt(80) : 0;
				stock.add(item(ids[i], count, res, nut[i]));
				long avail = count - res;
				if (avail > 0) food.add(new FoodStock<>(ids[i], "minecraft:" + ids[i], new FoodValue(nut[i], 0.5f, false), avail));
			}
			int adults = rnd.nextInt(8), babies = rnd.nextInt(5);
			long demand = adults * 10L + babies * 5L;
			int days = rnd.nextInt(8);
			long pool = FoodMath.nutrition(food);
			assertEquals(pool, Surplus.foodPool(stock));
			Reserves r = new Reserves(days, 64);
			for (StockItem<String> it : stock) {
				if (PriceTable.lot(it.id()).isEmpty()) continue;
				long s = of(it, stock, r, demand);
				if (s > 0) {
					// selling s of it still leaves the reserve (empty = nobody to feed = allowed)
					var left = FoodMath.daysLeft(pool - s * it.nutritionEach(), adults, babies);
					if (left.isPresent()) assertEquals(true, left.getAsDouble() >= days, "days left after selling " + s);
				}
			}
		}
	}
}
