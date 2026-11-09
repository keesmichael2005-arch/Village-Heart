package dev.villageheart.core.cook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Cook's recipe table (M13 plan 3.2 #2): a fixed table, not read from the game's recipe manager, so a data pack cannot change what
 * villagers cook. {@link #ALL} is in priority order: gain (output nutrition minus input nutrition) descending, then output nutrition
 * descending, then output id.
 */
public final class CookRecipes {
	private CookRecipes() {}

	private static CookRecipe smoke(String raw, int rawNutrition, String cooked, int cookedNutrition) {
		return new CookRecipe(cooked.substring("minecraft:".length()), RecipeKind.SMOKE, List.of(new Ingredient(raw, 1)), cooked, rawNutrition, cookedNutrition);
	}

	public static final List<CookRecipe> ALL;

	static {
		List<CookRecipe> r = new ArrayList<>();
		r.add(smoke("minecraft:beef", 3, "minecraft:cooked_beef", 8));
		r.add(smoke("minecraft:porkchop", 3, "minecraft:cooked_porkchop", 8));
		r.add(smoke("minecraft:chicken", 2, "minecraft:cooked_chicken", 6));
		r.add(smoke("minecraft:mutton", 2, "minecraft:cooked_mutton", 6));
		r.add(smoke("minecraft:rabbit", 3, "minecraft:cooked_rabbit", 5));
		r.add(smoke("minecraft:cod", 2, "minecraft:cooked_cod", 5));
		r.add(smoke("minecraft:salmon", 2, "minecraft:cooked_salmon", 6));
		r.add(smoke("minecraft:potato", 1, "minecraft:baked_potato", 5));
		r.add(new CookRecipe("bread", RecipeKind.CRAFT, List.of(new Ingredient("minecraft:wheat", 3)), "minecraft:bread", 0, 5));
		r.add(new CookRecipe("beetroot_soup", RecipeKind.CRAFT, List.of(new Ingredient("minecraft:beetroot", 6), new Ingredient("minecraft:bowl", 1)),
				"minecraft:beetroot_soup", 6, 6));
		r.sort(Comparator.comparingInt(CookRecipe::gain).reversed()
				.thenComparing(Comparator.comparingInt(CookRecipe::outputNutrition).reversed())
				.thenComparing(CookRecipe::outputId));
		ALL = List.copyOf(r);
	}

	/** The raw foods the smoker turns into something better: exactly what dinner treats as raw ({@code FoodRules.isRaw}). */
	public static Set<String> smokerInputs() {
		Set<String> out = new LinkedHashSet<>();
		for (CookRecipe r : ALL) if (r.kind() == RecipeKind.SMOKE) out.add(r.inputs().get(0).itemId());
		return Set.copyOf(out);
	}

	/** Every item id that is the input of some recipe. */
	public static Set<String> inputIds() {
		Set<String> out = new LinkedHashSet<>();
		for (CookRecipe r : ALL) for (Ingredient i : r.inputs()) out.add(i.itemId());
		return Set.copyOf(out);
	}

	public static Set<String> outputIds() {
		Set<String> out = new LinkedHashSet<>();
		for (CookRecipe r : ALL) out.add(r.outputId());
		return Set.copyOf(out);
	}
}
