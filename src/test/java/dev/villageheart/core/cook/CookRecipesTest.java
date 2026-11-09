package dev.villageheart.core.cook;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.food.FoodRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CookRecipesTest {
	private static CookRecipe byId(String id) {
		return CookRecipes.ALL.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void theTableOfThePlan() {
		assertEquals(10, CookRecipes.ALL.size());
		Map<String, String> smoke = Map.of("minecraft:beef", "minecraft:cooked_beef", "minecraft:porkchop", "minecraft:cooked_porkchop",
				"minecraft:chicken", "minecraft:cooked_chicken", "minecraft:mutton", "minecraft:cooked_mutton",
				"minecraft:rabbit", "minecraft:cooked_rabbit", "minecraft:cod", "minecraft:cooked_cod",
				"minecraft:salmon", "minecraft:cooked_salmon", "minecraft:potato", "minecraft:baked_potato");
		for (var e : smoke.entrySet()) {
			CookRecipe r = CookRecipes.ALL.stream().filter(x -> x.inputs().get(0).itemId().equals(e.getKey())).findFirst().orElseThrow();
			assertEquals(RecipeKind.SMOKE, r.kind());
			assertEquals(e.getValue(), r.outputId());
			assertEquals(List.of(new Ingredient(e.getKey(), 1)), r.inputs());
		}
		CookRecipe bread = byId("bread");
		assertEquals(RecipeKind.CRAFT, bread.kind());
		assertEquals(List.of(new Ingredient("minecraft:wheat", 3)), bread.inputs());
		CookRecipe soup = byId("beetroot_soup");
		assertEquals(RecipeKind.CRAFT, soup.kind());
		assertEquals(List.of(new Ingredient("minecraft:beetroot", 6), new Ingredient("minecraft:bowl", 1)), soup.inputs(), "soup needs a bowl");
		assertTrue(CookRecipes.ALL.stream().noneMatch(r -> r.outputId().contains("kelp") || r.inputs().get(0).itemId().contains("kelp")), "kelp is not food");
	}

	@Test
	void cookAndDinnerAgreeOnWhatIsRaw() {
		assertEquals(Balance.FOOD_RAW, CookRecipes.smokerInputs(), "the smoker's inputs are exactly dinner's raw list");
		for (String id : CookRecipes.smokerInputs()) assertTrue(FoodRules.isRaw(id), id);
		for (CookRecipe r : CookRecipes.ALL) assertFalse(FoodRules.isRaw(r.outputId()), r.outputId() + " is prepared food");
		assertTrue(CookRecipes.inputIds().containsAll(Set.of("minecraft:wheat", "minecraft:beetroot", "minecraft:bowl", "minecraft:beef")));
	}

	@Test
	void gainsAndPriorityOrder() {
		assertEquals(5, byId("cooked_beef").gain());
		assertEquals(5, byId("cooked_porkchop").gain());
		assertEquals(5, byId("bread").gain());
		assertEquals(4, byId("cooked_mutton").gain());
		assertEquals(4, byId("cooked_chicken").gain());
		assertEquals(4, byId("cooked_salmon").gain());
		assertEquals(4, byId("baked_potato").gain());
		assertEquals(3, byId("cooked_cod").gain());
		assertEquals(2, byId("cooked_rabbit").gain());
		assertEquals(0, byId("beetroot_soup").gain());
		List<String> order = CookRecipes.ALL.stream().map(CookRecipe::id).toList();
		assertEquals(List.of("cooked_beef", "cooked_porkchop", "bread", "cooked_chicken", "cooked_mutton", "cooked_salmon", "baked_potato",
				"cooked_cod", "cooked_rabbit", "beetroot_soup"), order);
		List<CookRecipe> sorted = new ArrayList<>(CookRecipes.ALL);
		for (int i = 1; i < sorted.size(); i++) assertTrue(sorted.get(i - 1).gain() >= sorted.get(i).gain());
	}

	@Test
	void unitsFromWhatIsHeld() {
		assertEquals(3, byId("bread").unitsFrom(Map.of("minecraft:wheat", 9)));
		assertEquals(2, byId("bread").unitsFrom(Map.of("minecraft:wheat", 8L)));
		assertEquals(0, byId("beetroot_soup").unitsFrom(Map.of("minecraft:beetroot", 6)), "no bowl");
		assertEquals(1, byId("beetroot_soup").unitsFrom(Map.of("minecraft:beetroot", 12, "minecraft:bowl", 1)));
		assertEquals(0, byId("cooked_beef").unitsFrom(Map.of()));
	}

	@Test
	void fuelHeat() {
		assertEquals(OptionalInt.of(1600), FuelTable.heat("minecraft:coal", false));
		assertEquals(OptionalInt.of(1600), FuelTable.heat("minecraft:charcoal", false));
		assertEquals(OptionalInt.of(300), FuelTable.heat("minecraft:oak_log", true));
		assertEquals(OptionalInt.empty(), FuelTable.heat("minecraft:crimson_stem", false), "a nether stem does not burn");
		assertEquals(OptionalInt.empty(), FuelTable.heat("minecraft:oak_planks", false));
		assertEquals(OptionalInt.empty(), FuelTable.heat("minecraft:stick", false));
		assertEquals(OptionalInt.empty(), FuelTable.heat("minecraft:lava_bucket", false));
		assertEquals(200, FuelTable.HEAT_PER_ITEM);
		assertEquals(8, FuelTable.COAL_HEAT / FuelTable.HEAT_PER_ITEM, "a coal makes 8 items");
		assertEquals(1.5, (double) FuelTable.LOG_HEAT / FuelTable.HEAT_PER_ITEM, "a log makes 1.5");
	}
}
