package dev.villageheart.core.cook;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.cook.BatchMath.Run;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class BatchMathTest {
	private static boolean log(String id) { return id.endsWith("_log"); }

	@Test
	void theRoadmapCaseBeefAndBread() {
		Run run = BatchMath.simulate(Map.of("minecraft:beef", 10L, "minecraft:wheat", 9L, "minecraft:coal", 4L), 0, BatchMathTest::log);
		assertEquals(3, run.steps().size());
		assertEquals("cooked_beef", run.steps().get(0).recipe().id());
		assertEquals(8, run.steps().get(0).units());
		assertEquals(1, run.steps().get(0).fuelItems());
		assertEquals(2, run.steps().get(1).units());
		assertEquals(1, run.steps().get(1).fuelItems());
		assertEquals("bread", run.steps().get(2).recipe().id());
		assertEquals(3, run.steps().get(2).units());
		assertEquals(0, run.steps().get(2).fuelItems(), "the bread runs on the heat that is left");
		assertEquals(Map.of("minecraft:coal", 2L), run.left());
		assertEquals(600, run.bank());
	}

	@Test
	void logsAreBurntInWholeItemsAndLeaveTheRestInTheBank() {
		Run run = BatchMath.simulate(Map.of("minecraft:potato", 8L, "minecraft:oak_log", 10L), 0, BatchMathTest::log);
		assertEquals(1, run.steps().size());
		assertEquals("baked_potato", run.steps().get(0).recipe().id());
		assertEquals(6, run.steps().get(0).fuelItems(), "6 logs = 1800 heat for 1600");
		assertEquals(Map.of("minecraft:oak_log", 4L), run.left());
		assertEquals(200, run.bank());
	}

	@Test
	void coalBeforeLogs() {
		Run run = BatchMath.simulate(Map.of("minecraft:beef", 8L, "minecraft:coal", 1L, "minecraft:oak_log", 10L), 0, BatchMathTest::log);
		assertEquals("minecraft:coal", run.steps().get(0).fuelId());
		assertEquals(Map.of("minecraft:oak_log", 10L), run.left());
		assertEquals(0, run.bank());
	}

	@Test
	void charcoalBeforeCoal() {
		assertEquals(List.of("minecraft:charcoal", "minecraft:coal", "minecraft:birch_log", "minecraft:oak_log"),
				BatchMath.fuelOrder(List.of("minecraft:oak_log", "minecraft:coal", "minecraft:birch_log", "minecraft:charcoal")));
	}

	@Test
	void aNonFuelIsNeverBurnt() {
		Run run = BatchMath.simulate(new TreeMap<>(Map.of("minecraft:beef", 8L, "minecraft:birch_log", 1L, "minecraft:crimson_stem", 1L)), 0, BatchMathTest::log);
		assertEquals(1, run.steps().size());
		assertEquals(1, run.steps().get(0).units(), "one birch log = 300 heat = one unit");
		assertEquals(100, run.bank());
		assertEquals(Map.of("minecraft:beef", 7L, "minecraft:crimson_stem", 1L), run.left());
	}

	@Test
	void noFuelNoWork() {
		Run run = BatchMath.simulate(Map.of("minecraft:beef", 10L, "minecraft:wheat", 9L), 0, BatchMathTest::log);
		assertTrue(run.steps().isEmpty());
		assertEquals(0, run.bank());
	}

	@Test
	void unitsByTimeNeverNegativeAndKeepsDinner() {
		int done = Balance.COOK_DONE_BY;
		assertEquals(2, BatchMath.unitsByTime(done - 500, 1.0, 200)); // (done - (done-500) - 200) / 125 = 2.4
		assertEquals(0, BatchMath.unitsByTime(done - 200, 1.0, 200));
		assertEquals(0, BatchMath.unitsByTime(done - 50, 1.0, 200), "never negative");
		assertEquals(2, BatchMath.unitsByTime(Math.floorMod(48_000L + (done - 500), 24_000L), 1.0, 200), "the same time on a later day");
		assertEquals(2, BatchMath.unitsByTime(done - 700, 1.0, 340));
		assertEquals(1, BatchMath.unitsByTime(done - 500, 0.5, 200), "hungry: half the units");
		assertEquals(0, BatchMath.unitsByTime(1000, 0.0, 0), "speed 0 cooks nothing");
	}

	@Test
	void fuelToFetch() {
		assertEquals(1, BatchMath.fuelToFetch(8, 1200, 1600));
		assertEquals(0, BatchMath.fuelToFetch(3, 1200, 1600));
		assertEquals(0, BatchMath.fuelToFetch(0, 0, 1600));
		assertEquals(6, BatchMath.fuelToFetch(8, 0, 300));
		assertEquals(1, BatchMath.fuelToFetch(1, 100, 300));
	}

	@Test
	void slotsCapTheBatch() {
		CookRecipe soup = CookRecipes.ALL.stream().filter(r -> r.id().equals("beetroot_soup")).findFirst().orElseThrow();
		CookRecipe beef = CookRecipes.ALL.get(0);
		assertEquals(5, BatchMath.unitsBySlots(soup, 8), "soup does not stack: 8 slots - 2 input - 1 fuel");
		assertEquals(0, BatchMath.unitsBySlots(soup, 3));
		assertTrue(BatchMath.unitsBySlots(beef, 8) >= 8);
		assertEquals(0, BatchMath.unitsBySlots(beef, 2));
	}

	@Test
	void pickFuelPrefersAKindThatCoversTheWholeBatch() {
		Map<String, Integer> heat = Map.of("minecraft:coal", 1600, "minecraft:oak_log", 300);
		List<String> order = List.of("minecraft:coal", "minecraft:oak_log");
		assertEquals("minecraft:oak_log", BatchMath.pickFuel(2400, heat, order, Map.of("minecraft:coal", 1L, "minecraft:oak_log", 10L)).id(),
				"one coal is not enough for 2400: the logs cover it");
		assertEquals("minecraft:coal", BatchMath.pickFuel(1600, heat, order, Map.of("minecraft:coal", 1L, "minecraft:oak_log", 10L)).id());
		assertEquals("minecraft:coal", BatchMath.pickFuel(2400, heat, order, Map.of("minecraft:coal", 1L, "minecraft:oak_log", 2L)).id(),
				"no kind covers it: the one that covers the most");
		assertEquals(BatchMath.FuelPick.NONE, BatchMath.pickFuel(100, heat, order, Map.of()));
	}
}
