package dev.villageheart.core.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Works out each build's reservations from one stockpile snapshot (docs/plans/M20.md 3.2 #9). Generic over the stock key {@code K} and the
 * holder {@code H} (the build id). One definition: {@code missing(m) = need(m) - carried(m) - reserved(m)}, and it reserves up to
 * {@code need(m) - carried(m)}: keys the build already holds first, then the largest stock, ties by key id, oldest build first.
 */
public final class MaterialPlanner {
	private MaterialPlanner() {}

	/**
	 * @param need    material id to the amount still to be placed (in first-use order)
	 * @param carried material id to the amount the builder already carries for this build
	 * @param held    stock key to the amount already reserved for this build
	 */
	public record BuildNeed<K, H>(H holder, Map<String, Integer> need, Map<String, Integer> carried, Map<K, Integer> held) {}

	public record Result<K, H>(Map<H, Map<K, Integer>> reserve, Map<H, Map<String, Integer>> missing) {}

	/**
	 * @param builds    oldest first
	 * @param stock     stock key to the amount that builds may reserve (the stock minus other consumers' reservations)
	 * @param satisfies whether a stock key can serve a material id
	 */
	public static <K, H> Result<K, H> plan(List<BuildNeed<K, H>> builds, Map<K, Integer> stock, BiPredicate<K, String> satisfies,
			Function<K, String> id) {
		Map<K, Integer> left = new HashMap<>(stock);
		List<K> keys = new ArrayList<>(stock.keySet());
		keys.sort(Comparator.comparing(id));
		Map<H, Map<K, Integer>> reserve = new LinkedHashMap<>();
		Map<H, Map<String, Integer>> missing = new LinkedHashMap<>();
		for (BuildNeed<K, H> b : builds) {
			Map<K, Integer> mine = new LinkedHashMap<>();
			Map<String, Integer> miss = new LinkedHashMap<>();
			for (Map.Entry<String, Integer> e : b.need().entrySet()) {
				String m = e.getKey();
				int wanted = Math.max(0, e.getValue() - b.carried().getOrDefault(m, 0));
				if (wanted == 0) continue;
				List<K> candidates = new ArrayList<>();
				for (K k : keys) if (left.getOrDefault(k, 0) > 0 && satisfies.test(k, m)) candidates.add(k);
				candidates.sort(Comparator.comparingInt((K k) -> b.held().getOrDefault(k, 0) > 0 ? 0 : 1)
						.thenComparing(Comparator.comparingInt((K k) -> left.get(k)).reversed()).thenComparing(id));
				int got = 0;
				for (K k : candidates) {
					if (got == wanted) break;
					int take = Math.min(wanted - got, left.get(k));
					mine.merge(k, take, Integer::sum);
					left.merge(k, -take, Integer::sum);
					got += take;
				}
				if (got < wanted) miss.put(m, wanted - got);
			}
			reserve.put(b.holder(), mine);
			missing.put(b.holder(), miss);
		}
		return new Result<>(reserve, missing);
	}
}
