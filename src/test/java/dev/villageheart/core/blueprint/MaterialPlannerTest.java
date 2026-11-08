package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.blueprint.FetchBatch.Take;
import dev.villageheart.core.blueprint.MaterialPlanner.BuildNeed;
import dev.villageheart.core.blueprint.MaterialPlanner.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-7 */
class MaterialPlannerTest {
	private static final String PLANKS = "#minecraft:planks";

	private static boolean satisfies(String key, String material) {
		if (material.equals(PLANKS)) return key.endsWith("_planks");
		return key.equals(material);
	}

	private static Result<String, String> plan(List<BuildNeed<String, String>> builds, Map<String, Integer> stock) {
		return MaterialPlanner.plan(builds, stock, MaterialPlannerTest::satisfies, k -> k);
	}

	private static BuildNeed<String, String> need(String holder, int planks, int carried, Map<String, Integer> held) {
		return new BuildNeed<>(holder, Map.of(PLANKS, planks), carried == 0 ? Map.of() : Map.of(PLANKS, carried), held);
	}

	@Test
	void largestStockFirstOneWoodWhenPossible() {
		Result<String, String> r = plan(List.of(need("a", 119, 0, Map.of())), Map.of("oak_planks", 100, "spruce_planks", 30));
		assertEquals(Map.of("oak_planks", 100, "spruce_planks", 19), r.reserve().get("a"));
		assertTrue(r.missing().get("a").isEmpty());
		Result<String, String> one = plan(List.of(need("a", 20, 0, Map.of())), Map.of("oak_planks", 100, "spruce_planks", 30));
		assertEquals(Map.of("oak_planks", 20), one.reserve().get("a"));
	}

	@Test
	void keepsTheKeysItAlreadyHolds() {
		Result<String, String> r = plan(List.of(need("a", 40, 0, Map.of("spruce_planks", 10))), Map.of("oak_planks", 100, "spruce_planks", 30));
		assertEquals(Map.of("spruce_planks", 30, "oak_planks", 10), r.reserve().get("a"));
	}

	@Test
	void tiesBreakByKeyId() {
		Result<String, String> r = plan(List.of(need("a", 10, 0, Map.of())), Map.of("birch_planks", 50, "acacia_planks", 50));
		assertEquals(Map.of("acacia_planks", 10), r.reserve().get("a"));
	}

	@Test
	void missingIsNeedMinusCarriedMinusReserved() {
		// 55 planks still to place, 64 of 119 fetched and carried... carried 10 of the remainder, 43 in stock: missing 12
		Result<String, String> r = plan(List.of(need("a", 55, 0, Map.of())), Map.of("oak_planks", 43));
		assertEquals(Map.of("oak_planks", 43), r.reserve().get("a"));
		assertEquals(Map.of(PLANKS, 12), r.missing().get("a"));
		Result<String, String> carried = plan(List.of(need("a", 55, 20, Map.of())), Map.of("oak_planks", 20));
		assertEquals(Map.of("oak_planks", 20), carried.reserve().get("a"));
		assertEquals(Map.of(PLANKS, 15), carried.missing().get("a"));
		Result<String, String> done = plan(List.of(need("a", 55, 55, Map.of())), Map.of("oak_planks", 20));
		assertTrue(done.reserve().get("a").isEmpty());
		assertTrue(done.missing().get("a").isEmpty());
	}

	@Test
	void theOlderBuildWinsScarceStock() {
		Result<String, String> r = plan(List.of(need("old", 50, 0, Map.of()), need("new", 50, 0, Map.of())), Map.of("oak_planks", 60));
		assertEquals(Map.of("oak_planks", 50), r.reserve().get("old"));
		assertEquals(Map.of("oak_planks", 10), r.reserve().get("new"));
		assertEquals(Map.of(PLANKS, 40), r.missing().get("new"));
		assertTrue(r.missing().get("old").isEmpty());
	}

	@Test
	void nothingInStockIsAllMissing() {
		Result<String, String> r = plan(List.of(need("a", 7, 0, Map.of())), Map.of("cobblestone", 100));
		assertTrue(r.reserve().get("a").isEmpty());
		assertEquals(Map.of(PLANKS, 7), r.missing().get("a"));
	}

	// ---- fetch batches ----

	private static int stack(String material) { return material.equals("bed") ? 1 : 64; }

	private static List<String> steps(Object... pairs) {
		java.util.ArrayList<String> out = new java.util.ArrayList<>();
		for (int i = 0; i < pairs.length; i += 2) for (int n = 0; n < (Integer) pairs[i + 1]; n++) out.add((String) pairs[i]);
		return out;
	}

	@Test
	void batchStopsAtOneStackWorth() {
		Map<String, Integer> reserved = new LinkedHashMap<>(Map.of("cobblestone", 49, PLANKS, 119));
		List<Take> t = FetchBatch.plan(steps("cobblestone", 49, PLANKS, 119), Map.of(), reserved, MaterialPlannerTest::stack);
		assertEquals(List.of(new Take("cobblestone", 49), new Take(PLANKS, 15)), t);
	}

	@Test
	void batchNeverTakesMoreThanReservedOrNeeded() {
		List<Take> t = FetchBatch.plan(steps(PLANKS, 30), Map.of(PLANKS, 25), Map.of(PLANKS, 100), MaterialPlannerTest::stack);
		assertEquals(List.of(new Take(PLANKS, 5)), t);
		List<Take> few = FetchBatch.plan(steps(PLANKS, 30), Map.of(), Map.of(PLANKS, 12), MaterialPlannerTest::stack);
		assertEquals(List.of(new Take(PLANKS, 12)), few);
		assertTrue(FetchBatch.plan(steps(PLANKS, 30), Map.of(), Map.of(), MaterialPlannerTest::stack).isEmpty());
	}

	@Test
	void aBedIsABatchOfItsOwn() {
		List<Take> t = FetchBatch.plan(steps("bed", 1, PLANKS, 10), Map.of(), Map.of("bed", 1, PLANKS, 10), MaterialPlannerTest::stack);
		assertEquals(List.of(new Take("bed", 1)), t);
	}

	@Test
	void batchStopsAtFourSlots() {
		// four materials of one item each (stack 64): 4 slots used while the load is only 4/64
		List<Take> t = FetchBatch.plan(steps("a", 1, "b", 1, "c", 1, "d", 1, "e", 1), Map.of(),
				Map.of("a", 1, "b", 1, "c", 1, "d", 1, "e", 1), MaterialPlannerTest::stack);
		assertEquals(4, t.size());
	}
}
