package dev.villageheart.core.cook;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.cook.CookPlanner.Batch;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CookKeepTest {
	private static CookRecipe recipe(String id) {
		return CookRecipes.ALL.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
	}

	private static int keep(String id, boolean fuel, Map<String, Integer> carried, Optional<Batch> batch, boolean returning, boolean home) {
		return CookKeep.keep(id, fuel, carried, batch, returning, home);
	}

	@Test
	void aBatchKeepsItsInputsFuelAndOutputsSoFar() {
		Map<String, Integer> carried = Map.of("minecraft:beef", 8, "minecraft:coal", 1, "minecraft:cooked_beef", 3, "minecraft:stick", 2);
		Optional<Batch> batch = Optional.of(new Batch(recipe("cooked_beef"), 8, "minecraft:coal"));
		assertEquals(8, keep("minecraft:beef", false, carried, batch, false, false));
		assertEquals(1, keep("minecraft:coal", true, carried, batch, false, false));
		assertEquals(3, keep("minecraft:cooked_beef", false, carried, batch, false, false), "the outputs stay until the batch is done");
		assertEquals(0, keep("minecraft:stick", false, carried, batch, false, false), "anything foreign is handed in");
	}

	@Test
	void inputsAboveTheBatchAreHandedIn() {
		Map<String, Integer> carried = Map.of("minecraft:beef", 12);
		assertEquals(5, keep("minecraft:beef", false, carried, Optional.of(new Batch(recipe("cooked_beef"), 5, "")), false, false));
	}

	@Test
	void afterAReloadWholeUnitsAreKept() {
		Map<String, Integer> carried = Map.of("minecraft:wheat", 7, "minecraft:coal", 1, "minecraft:cooked_beef", 2);
		assertEquals(6, keep("minecraft:wheat", false, carried, Optional.empty(), false, false));
		assertEquals(1, keep("minecraft:coal", true, carried, Optional.empty(), false, false));
		assertEquals(0, keep("minecraft:cooked_beef", false, carried, Optional.empty(), false, false), "no batch: the outputs go to Storage");
	}

	@Test
	void fiveBeetrootAndABowlAreNotAUnit() {
		Map<String, Integer> carried = Map.of("minecraft:beetroot", 5, "minecraft:bowl", 1, "minecraft:coal", 1);
		assertEquals(0, keep("minecraft:beetroot", false, carried, Optional.empty(), false, false));
		assertEquals(0, keep("minecraft:bowl", false, carried, Optional.empty(), false, false));
		assertEquals(0, keep("minecraft:coal", true, carried, Optional.empty(), false, false), "fuel is kept only while inputs are");
		assertEquals(6, keep("minecraft:beetroot", false, Map.of("minecraft:beetroot", 6, "minecraft:bowl", 1), Optional.empty(), false, false));
	}

	@Test
	void returningAndHomeTimeKeepNothing() {
		Map<String, Integer> carried = Map.of("minecraft:beef", 8, "minecraft:coal", 1, "minecraft:cooked_beef", 3);
		Optional<Batch> batch = Optional.of(new Batch(recipe("cooked_beef"), 8, "minecraft:coal"));
		for (String id : carried.keySet()) {
			assertEquals(0, keep(id, id.equals("minecraft:coal"), carried, batch, true, false), id + " while returning");
			assertEquals(0, keep(id, id.equals("minecraft:coal"), carried, batch, false, true), id + " at GO_HOME");
		}
	}
}
