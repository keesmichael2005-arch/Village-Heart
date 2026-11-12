package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-19. Wealth is the treasury plus each stack's count times its value. */
class ItemValueTest {
	@Test
	void valuesAndWealth() {
		assertEquals(1, ItemValue.of("minecraft:emerald"));
		assertEquals(2, ItemValue.of("minecraft:diamond"));
		assertEquals(0.25, ItemValue.of("minecraft:iron_ingot"));
		assertEquals(0.25, ItemValue.of("minecraft:copper_ingot"));
		assertEquals(0.25, ItemValue.of("minecraft:gold_ingot"));
		assertEquals(1.0 / 64, ItemValue.of("minecraft:stick"));
		assertEquals(20, ItemValue.wealth(10, Map.of(
				"minecraft:emerald", 4,
				"minecraft:diamond", 2,
				"minecraft:iron_ingot", 4,
				"minecraft:stick", 64)));
	}
}
