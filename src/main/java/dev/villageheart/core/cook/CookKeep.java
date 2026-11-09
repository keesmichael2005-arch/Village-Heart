package dev.villageheart.core.cook;

import java.util.Map;
import java.util.Optional;

/**
 * What the Cook never hands in (M13 plan 3.5): the inputs, the fuel and the outputs made so far of the batch in progress, so no deposit
 * trip splits a batch. At the end of the work day, or once the cook hands back ({@code returning}), nothing is kept.
 */
public final class CookKeep {
	private CookKeep() {}

	/**
	 * @param itemId    the carried item
	 * @param isFuel    whether it is fuel (coal, charcoal, a burnable log)
	 * @param carried   everything carried (id to count; the glue counts component-free stacks, a named item is never kept)
	 * @param batch     the batch in progress, if any
	 * @param returning the cook is handing its things back
	 * @param homeTime  the work day is over
	 */
	public static int keep(String itemId, boolean isFuel, Map<String, Integer> carried, Optional<CookPlanner.Batch> batch, boolean returning, boolean homeTime) {
		if (returning || homeTime) return 0;
		int have = carried.getOrDefault(itemId, 0);
		if (have <= 0) return 0;
		if (batch.isPresent()) {
			CookPlanner.Batch b = batch.get();
			if (itemId.equals(b.recipe().outputId())) return have; // the outputs so far
			int perUnit = b.recipe().inputCount(itemId);
			if (perUnit > 0) return Math.min(have, perUnit * b.units());
			return isFuel ? have : 0;
		}
		// no batch (after a reload): keep what forms whole units of some recipe, and the fuel while such inputs are carried
		int whole = 0;
		boolean anyUnit = false;
		for (CookRecipe r : CookRecipes.ALL) {
			int units = r.unitsFrom(carried);
			if (units < 1) continue;
			anyUnit = true;
			int perUnit = r.inputCount(itemId);
			if (perUnit > 0) whole = Math.max(whole, perUnit * units);
		}
		if (whole > 0) return Math.min(have, whole);
		return isFuel && anyUnit ? have : 0;
	}
}
