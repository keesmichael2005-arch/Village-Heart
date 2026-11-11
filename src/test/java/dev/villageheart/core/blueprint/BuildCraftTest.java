package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.blueprint.BuildCraft.Input;
import dev.villageheart.core.blueprint.BuildCraft.Recipe;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A builder turns logs into stairs, and refuses a recipe it was not given. */
class BuildCraftTest {
	private static final List<Recipe> RECIPES = List.of(
			new Recipe("minecraft:oak_planks", 4, List.of(new Input("minecraft:oak_log", 1)), false),
			new Recipe("minecraft:oak_stairs", 4, List.of(new Input("minecraft:oak_planks", 6)), false),
			new Recipe("minecraft:glass", 1, List.of(new Input("minecraft:sand", 1)), true));

	@Test
	void logsBecomeStairs() {
		var plan = BuildCraft.plan("minecraft:oak_stairs", 4, Map.of("minecraft:oak_log", 2), RECIPES).orElseThrow();
		assertEquals(2, plan.consumed().get("minecraft:oak_log"));
		assertEquals(List.of("minecraft:oak_planks", "minecraft:oak_stairs"), plan.steps().stream().map(BuildCraft.Step::output).toList());
		assertFalse(plan.steps().get(1).smelt());
	}

	@Test
	void missingIngredientIsImpossible() {
		assertTrue(BuildCraft.plan("minecraft:oak_stairs", 4, Map.of("minecraft:oak_log", 1), RECIPES).isEmpty());
	}

	@Test
	void usesStockBeforeCrafting() {
		var plan = BuildCraft.plan("minecraft:oak_stairs", 4, Map.of("minecraft:oak_planks", 6), RECIPES).orElseThrow();
		assertEquals(6, plan.consumed().get("minecraft:oak_planks"));
		assertEquals(1, plan.steps().size());
		assertEquals("minecraft:oak_stairs", plan.steps().get(0).output());
	}

	@Test
	void smeltingIsMarked() {
		var plan = BuildCraft.plan("minecraft:glass", 3, Map.of("minecraft:sand", 3), RECIPES).orElseThrow();
		assertTrue(plan.steps().get(0).smelt());
		assertEquals(3, plan.steps().get(0).times());
	}

	@Test
	void unknownItemIsImpossible() {
		assertTrue(BuildCraft.plan("minecraft:diamond_sword", 1, Map.of("minecraft:diamond", 2, "minecraft:stick", 1), RECIPES).isEmpty());
	}
}
