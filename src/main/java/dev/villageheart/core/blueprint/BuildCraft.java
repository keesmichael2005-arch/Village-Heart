package dev.villageheart.core.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How a builder turns raw stock into a building block (P4). Pure: the glue supplies vanilla crafting and smelting recipes whose output is a
 * building block. A plan never crafts tools, food or redstone. Depth is capped so a recipe that loops cannot run away.
 */
public final class BuildCraft {
	private BuildCraft() {}

	public static final int MAX_DEPTH = 6;

	/** One vanilla recipe reduced to item ids. {@code smelt} means it also needs fuel, which the glue withdraws. */
	public record Recipe(String output, int count, List<Input> inputs, boolean smelt) {
		public Recipe {
			inputs = List.copyOf(inputs);
			if (count <= 0) throw new IllegalArgumentException("count");
		}
	}

	public record Input(String item, int count) {
		public Input {
			if (count <= 0) throw new IllegalArgumentException("count");
		}
	}

	/** One crafting or smelting action, in the order the ingredients have to exist. */
	public record Step(String output, int times, boolean smelt) {}

	public record Plan(List<Step> steps, Map<String, Integer> consumed) {
		public Plan {
			steps = List.copyOf(steps);
			consumed = Map.copyOf(consumed);
		}
	}

	/**
	 * A way to have {@code count} of {@code output} in hand, consuming {@code stock}. Empty when it cannot be done.
	 * {@code stock} is not modified.
	 */
	public static Optional<Plan> plan(String output, int count, Map<String, Integer> stock, List<Recipe> recipes) {
		if (count <= 0) return Optional.of(new Plan(List.of(), Map.of()));
		Map<String, Integer> left = new HashMap<>(stock);
		List<Step> steps = new ArrayList<>();
		Map<String, Integer> consumed = new HashMap<>();
		if (!produce(left, output, count, recipes, 0, steps, consumed)) return Optional.empty();
		return Optional.of(new Plan(steps, consumed));
	}

	private static boolean produce(Map<String, Integer> stock, String item, int count, List<Recipe> recipes, int depth, List<Step> steps,
			Map<String, Integer> consumed) {
		if (count <= 0) return true;
		int have = stock.getOrDefault(item, 0);
		int use = Math.min(have, count);
		if (use > 0) {
			stock.put(item, have - use);
			consumed.merge(item, use, Integer::sum);
			count -= use;
		}
		if (count <= 0) return true;
		if (depth >= MAX_DEPTH) return false;
		Recipe recipe = null;
		for (Recipe r : recipes) if (r.output().equals(item)) { recipe = r; break; }
		if (recipe == null) return false;
		int times = (count + recipe.count() - 1) / recipe.count();
		for (Input in : recipe.inputs()) {
			if (!produce(stock, in.item(), in.count() * times, recipes, depth + 1, steps, consumed)) return false;
		}
		stock.merge(item, recipe.count() * times, Integer::sum);
		steps.add(new Step(item, times, recipe.smelt()));
		return produce(stock, item, count, recipes, depth, steps, consumed);
	}
}
