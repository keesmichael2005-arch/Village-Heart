package dev.villageheart.core.food;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who eats what at dinner (M11 plan 3.2 #4). Eaters in order: hungriest first, then workers in hiring order, then non-worker adults
 * before children, then id. Each eater whose need can still be met from the food left eats the best item, then the next best, until it has
 * eaten its need; one that cannot be fed eats nothing (a partial meal would not make it fed). Deterministic for any input order.
 */
public final class DinnerPlanner {
	private DinnerPlanner() {}

	private static final Comparator<Eater> ORDER = Comparator.<Eater>comparingInt(e -> -e.hungryDays())
			.thenComparingInt(e -> e.workerIndex() < 0 ? Integer.MAX_VALUE : e.workerIndex())
			.thenComparing(Eater::child)
			.thenComparing(Eater::id);

	public static <K> DinnerPlan<K> plan(List<FoodStock<K>> stock, List<Eater> eaters) {
		List<FoodStock<K>> food = new ArrayList<>();
		for (FoodStock<K> s : stock) if (s.available() > 0 && s.value().nutrition() > 0) food.add(s);
		food.sort(FoodRules.<K>bestFirst());
		long[] left = new long[food.size()];
		long total = 0;
		for (int i = 0; i < left.length; i++) {
			left[i] = food.get(i).available();
			total += left[i] * food.get(i).value().nutrition();
		}
		List<Eater> order = new ArrayList<>(eaters);
		order.sort(ORDER);
		List<Meal<K>> meals = new ArrayList<>();
		Map<K, Long> consumed = new HashMap<>();
		int fed = 0;
		long eatenNutrition = 0, eatenItems = 0;
		for (Eater e : order) {
			int need = e.need();
			if (total < need) {
				meals.add(new Meal<>(e.id(), false, 0, Map.of()));
				continue;
			}
			Map<K, Integer> items = new LinkedHashMap<>();
			int eaten = 0;
			for (int i = 0; i < left.length && eaten < need; i++) {
				int n = food.get(i).value().nutrition();
				if (left[i] <= 0) continue;
				long want = (need - eaten + n - 1) / n;
				long take = Math.min(want, left[i]);
				left[i] -= take;
				total -= take * n;
				eaten += (int) (take * n);
				items.merge(food.get(i).key(), (int) take, Integer::sum);
				consumed.merge(food.get(i).key(), take, Long::sum);
				eatenItems += take;
			}
			eatenNutrition += eaten;
			fed++;
			meals.add(new Meal<>(e.id(), true, eaten, items));
		}
		return new DinnerPlan<>(meals, consumed, order.size(), fed, order.size() - fed, eatenNutrition, eatenItems);
	}
}
