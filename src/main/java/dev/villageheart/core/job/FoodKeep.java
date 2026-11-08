package dev.villageheart.core.job;

import dev.villageheart.core.Balance;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The food every worker keeps for itself and for breeding (M10 plan 3.2 #9, replaces M7's "keep all food"): after the job's own keep, food
 * is kept until the kept food points reach {@link Balance#WORKER_FOOD_KEEP} (vanilla's breeding threshold), bread first.
 */
public final class FoodKeep {
	private FoodKeep() {}

	/** Food points per item, in the order they are kept. Must equal {@code Villager.FOOD_POINTS} (asserted by a GameTest). */
	public static final Map<String, Integer> POINTS = new LinkedHashMap<>();

	static {
		POINTS.put("minecraft:bread", 4);
		POINTS.put("minecraft:potato", 1);
		POINTS.put("minecraft:carrot", 1);
		POINTS.put("minecraft:beetroot", 1);
	}

	/**
	 * @param inventory item id to carried count
	 * @param jobKept   item id to the count the job already keeps (at most the carried count)
	 * @return item id to the count kept in addition (only entries above 0)
	 */
	public static Map<String, Integer> topUp(Map<String, Integer> inventory, Map<String, Integer> jobKept) {
		int points = 0;
		for (Map.Entry<String, Integer> e : POINTS.entrySet()) points += jobKept.getOrDefault(e.getKey(), 0) * e.getValue();
		int need = Balance.WORKER_FOOD_KEEP - points;
		Map<String, Integer> extra = new HashMap<>();
		for (Map.Entry<String, Integer> e : POINTS.entrySet()) {
			if (need <= 0) break;
			int available = inventory.getOrDefault(e.getKey(), 0) - jobKept.getOrDefault(e.getKey(), 0);
			if (available <= 0) continue;
			int n = Math.min(available, (need + e.getValue() - 1) / e.getValue());
			extra.put(e.getKey(), n);
			need -= n * e.getValue();
		}
		return extra;
	}
}
