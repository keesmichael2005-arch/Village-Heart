package dev.villageheart.core.cook;

import java.util.List;
import java.util.Map;

/**
 * One output item made from {@code inputs}. The nutrition figures are vanilla's food values, pinned here and compared with the game's
 * data components by a GameTest ({@code tablesMatchVanilla}); {@link #gain()} orders the recipes.
 */
public record CookRecipe(String id, RecipeKind kind, List<Ingredient> inputs, String outputId, int inputNutrition, int outputNutrition) {
	public CookRecipe {
		inputs = List.copyOf(inputs);
		if (inputs.isEmpty()) throw new IllegalArgumentException("a recipe needs inputs");
	}

	/** What one output item adds to the village's food: output nutrition minus the nutrition of the inputs it used. */
	public int gain() { return outputNutrition - inputNutrition; }

	/** How many output items {@code have} (item id to count) can make. */
	public int unitsFrom(Map<String, ? extends Number> have) {
		int units = Integer.MAX_VALUE;
		for (Ingredient i : inputs) {
			Number n = have.get(i.itemId());
			units = Math.min(units, n == null ? 0 : (int) Math.min(Integer.MAX_VALUE, n.longValue() / i.count()));
		}
		return units;
	}

	public boolean usesInput(String itemId) { return inputCount(itemId) > 0; }

	/** How many of {@code itemId} one output item takes (0 when it is not an input). */
	public int inputCount(String itemId) {
		for (Ingredient i : inputs) if (i.itemId().equals(itemId)) return i.count();
		return 0;
	}
}
