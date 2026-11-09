package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.job.ToolRequirement.Mode;
import org.junit.jupiter.api.Test;

class SupplyRuleTest {
	@Test
	void withoutASmithItMayAlwaysStart() {
		assertTrue(SupplyRule.selfCraftMayStart(false, SlotState.WORN, Mode.BONUS, 0));
		assertTrue(SupplyRule.selfCraftMayStart(false, SlotState.MISSING, Mode.REQUIRED, 0));
	}

	@Test
	void withASmithOnlyAfterTheFallback() {
		assertFalse(SupplyRule.selfCraftMayStart(true, SlotState.MISSING, Mode.REQUIRED, 2999));
		assertTrue(SupplyRule.selfCraftMayStart(true, SlotState.MISSING, Mode.REQUIRED, 3000));
		assertFalse(SupplyRule.selfCraftMayStart(true, SlotState.WORN, Mode.REQUIRED, 99_999));
		assertFalse(SupplyRule.selfCraftMayStart(true, SlotState.MISSING, Mode.BONUS, 99_999));
	}
}
