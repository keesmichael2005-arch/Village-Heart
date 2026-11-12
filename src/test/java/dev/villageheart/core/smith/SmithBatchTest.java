package dev.villageheart.core.smith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SmithBatchTest {
	private static final Map<String, Integer> HEAT = Map.of("minecraft:coal", 1600);
	private static final Map<String, Long> TEN = Map.of("minecraft:raw_iron", 10L);

	private static SmithBatch.Plan batch(Map<String, Long> ore, Map<String, Integer> fuel, int bank, boolean cook, int slots, long day) {
		return SmithBatch.plan(ore, fuel, HEAT, bank, cook, slots, day, 1, 0);
	}

	@Test
	void oneCoalSmeltsEightThenTheRestWaitsForFuel() {
		SmithBatch.Plan first = batch(TEN, Map.of("minecraft:coal", 1), 0, false, 16, 1000);
		assertEquals(8, first.step().orElseThrow().units());
		assertEquals("minecraft:iron_ingot", first.step().orElseThrow().outputId());
		assertEquals("minecraft:coal", first.step().orElseThrow().fuelId());
		assertEquals(1, first.step().orElseThrow().fuelItems());
		SmithBatch.Plan rest = batch(Map.of("minecraft:raw_iron", 2L), Map.of(), 0, false, 16, 1000);
		assertTrue(rest.step().isEmpty());
		assertTrue(rest.needsFuel());
		SmithFacts facts = SmithFacts.morning();
		facts.stock = Map.of("minecraft:raw_iron", 2L);
		facts.fuelHeat = HEAT;
		SmithDecision.Idle idle = (SmithDecision.Idle) SmithPlanner.decide(facts);
		assertEquals(SmithDecision.Hint.NEEDS_FUEL, idle.hint());
	}

	@Test
	void aHiredCookKeepsTwoCoal() {
		assertEquals(0, SmithBatch.availableHeat(Map.of("minecraft:coal", 2), HEAT, true));
		assertEquals(1600, SmithBatch.availableHeat(Map.of("minecraft:coal", 3), HEAT, true));
		SmithBatch.Plan oneCoal = batch(TEN, Map.of("minecraft:coal", 3), 0, true, 16, 1000);
		assertEquals(8, oneCoal.step().orElseThrow().units());
		assertEquals(1, oneCoal.step().orElseThrow().fuelItems());
	}

	@Test
	void aFullInventorySmeltsNothing() {
		SmithBatch.Plan full = batch(TEN, Map.of("minecraft:coal", 4), 0, false, 0, 1000);
		assertTrue(full.step().isEmpty());
		assertFalse(full.needsFuel());
	}
}
