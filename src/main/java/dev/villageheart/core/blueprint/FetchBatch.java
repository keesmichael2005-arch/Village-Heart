package dev.villageheart.core.blueprint;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What a builder takes from Storage on one trip (docs/plans/M20.md 3.2 #10): the materials of the next PLACE steps in step order, only
 * what is reserved for its build, until the batch is one stack's worth or fills {@link Balance#BUILDER_FETCH_SLOTS} slots.
 */
public final class FetchBatch {
	private FetchBatch() {}

	public record Take(String material, int n) {}

	/**
	 * @param stepMaterials the material id of each PLACE step from the cursor on, in step order
	 * @param carried       material id to the amount already carried for the build
	 * @param reserved      material id to the amount reserved for the build and still in Storage
	 * @param stackSize     max stack size of the item used for a material id
	 */
	public static List<Take> plan(List<String> stepMaterials, Map<String, Integer> carried, Map<String, Integer> reserved,
			ToIntFunction<String> stackSize) {
		Map<String, Integer> demand = new LinkedHashMap<>();
		for (String m : stepMaterials) demand.merge(m, 1, Integer::sum);
		List<Take> out = new ArrayList<>();
		double load = 0;
		int slots = 0;
		for (Map.Entry<String, Integer> e : demand.entrySet()) {
			String m = e.getKey();
			int stack = Math.max(1, stackSize.applyAsInt(m));
			int want = Math.max(0, e.getValue() - carried.getOrDefault(m, 0));
			int n = Math.min(want, reserved.getOrDefault(m, 0));
			if (n <= 0) continue;
			// take at most what completes the batch
			int byLoad = (int) Math.ceil((Balance.BUILDER_FETCH_LOAD - load) * stack - 1e-9);
			int bySlots = (Balance.BUILDER_FETCH_SLOTS - slots) * stack;
			n = Math.min(n, Math.max(1, Math.min(byLoad, bySlots)));
			out.add(new Take(m, n));
			load += (double) n / stack;
			slots += (n + stack - 1) / stack;
			if (load >= Balance.BUILDER_FETCH_LOAD - 1e-9 || slots >= Balance.BUILDER_FETCH_SLOTS) break;
		}
		return out;
	}
}
