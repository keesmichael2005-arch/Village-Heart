package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolRulesTest {
	@Test
	void everyAxeOfTheTagMapsToItsTier() {
		// the seven members of #minecraft:axes in 1.21.11 (hand-written)
		assertEquals(Optional.of(ToolTier.WOOD), ToolRules.tierOf("minecraft:wooden_axe"));
		assertEquals(Optional.of(ToolTier.STONE), ToolRules.tierOf("minecraft:stone_axe"));
		assertEquals(Optional.of(ToolTier.COPPER), ToolRules.tierOf("minecraft:copper_axe"));
		assertEquals(Optional.of(ToolTier.GOLD), ToolRules.tierOf("minecraft:golden_axe"));
		assertEquals(Optional.of(ToolTier.IRON), ToolRules.tierOf("minecraft:iron_axe"));
		assertEquals(Optional.of(ToolTier.DIAMOND), ToolRules.tierOf("minecraft:diamond_axe"));
		assertEquals(Optional.of(ToolTier.NETHERITE), ToolRules.tierOf("minecraft:netherite_axe"));
	}

	@Test
	void nonToolsAreEmpty() {
		assertEquals(Optional.empty(), ToolRules.tierOf("minecraft:stick"));
		assertEquals(Optional.empty(), ToolRules.tierOf("minecraft:stone"));
		assertEquals(Optional.empty(), ToolRules.tierOf("minecraft:stone_bricks"));
		assertEquals(Optional.empty(), ToolRules.tierOf("minecraft:iron_ingot"));
		assertEquals(Optional.empty(), ToolRules.tierOf("othermod:ruby_axe"));
	}

	@Test
	void multipliersFollowTheBalanceTable() {
		for (ToolTier t : ToolTier.values()) assertEquals(Balance.TOOL_SPEED[t.ordinal()], ToolRules.multiplier(t));
		assertEquals(Balance.TOOL_SPEED.length, ToolTier.values().length);
		assertEquals(1.25, ToolRules.multiplier(ToolTier.STONE));
		assertEquals(1.0, ToolRules.multiplier(ToolTier.WOOD));
		assertEquals(1.5, ToolRules.multiplier(ToolTier.IRON));
		assertEquals(1.75, ToolRules.multiplier(ToolTier.DIAMOND));
	}
}
