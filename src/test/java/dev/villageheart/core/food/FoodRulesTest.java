package dev.villageheart.core.food;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FoodRulesTest {
	static FoodStock<String> f(String id, int n, float sat, boolean raw) {
		return new FoodStock<>(id, "minecraft:" + id, new FoodValue(n, sat, raw), 10);
	}

	@Test void rankingIsBestFirstAndCookedBeforeRaw() {
		List<FoodStock<String>> all = new ArrayList<>(List.of(
				f("cooked_beef", 8, 12.8f, false), f("bread", 5, 6.0f, false), f("baked_potato", 5, 6.0f, false),
				f("carrot", 3, 3.6f, false), f("beef", 3, 1.8f, true), f("potato", 1, 0.6f, true), f("cod", 2, 0.4f, true)));
		for (int seed = 0; seed < 20; seed++) {
			Collections.shuffle(all, new Random(seed));
			List<FoodStock<String>> sorted = new ArrayList<>(all);
			sorted.sort(FoodRules.<String>bestFirst());
			List<String> ids = sorted.stream().map(FoodStock::key).toList();
			// prepared first (nutrition desc, saturation desc, id asc), then raw
			assertEquals(List.of("cooked_beef", "baked_potato", "bread", "carrot", "beef", "cod", "potato"), ids);
		}
	}

	@Test void carrotBeatsRawBeefAtEqualNutrition() {
		List<FoodStock<String>> l = new ArrayList<>(List.of(f("beef", 3, 1.8f, true), f("carrot", 3, 3.6f, false)));
		l.sort(FoodRules.<String>bestFirst());
		assertEquals("carrot", l.get(0).key());
	}

	@Test void rawAndNeverEat() {
		for (String id : Balance.FOOD_RAW) assertTrue(FoodRules.isRaw(id), id);
		assertEquals(8, Balance.FOOD_RAW.size());
		for (String id : List.of("minecraft:carrot", "minecraft:apple", "minecraft:bread")) assertFalse(FoodRules.isRaw(id), id);
		for (String id : Balance.FOOD_NEVER_EAT) assertTrue(FoodRules.neverEat(id), id);
		assertFalse(FoodRules.neverEat("minecraft:bread"));
		assertTrue(FoodRules.neverEat("minecraft:rotten_flesh"));
	}

	/** The Heart screen counts food through the item tag villageheart:not_village_food: it must name the same items as the dinner. */
	@Test void neverEatSetEqualsTheItemTag() throws Exception {
		String json = Files.readString(Path.of("src/main/resources/data/villageheart/tags/item/not_village_food.json"));
		Set<String> tag = new TreeSet<>();
		Matcher m = Pattern.compile("\"(minecraft:[a-z_]+)\"").matcher(json);
		while (m.find()) tag.add(m.group(1));
		assertEquals(new TreeSet<>(Balance.FOOD_NEVER_EAT), tag);
	}
}
