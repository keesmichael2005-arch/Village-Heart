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
		assertEquals(2.0, ToolRules.multiplier(ToolTier.STONE));
		assertEquals(1.0, ToolRules.multiplier(ToolTier.WOOD));
		assertEquals(3.0, ToolRules.multiplier(ToolTier.IRON));
		assertEquals(4.0, ToolRules.multiplier(ToolTier.DIAMOND));
	}

	/** Human decision 2026-11-09: tool work speed follows vanilla 1.21.11 mining speeds (wood 2, stone 4, copper 5, iron 6, diamond 8, netherite 9, gold 12) / 2. */
	@Test
	void everyMultiplierIsTheVanillaMiningSpeedHalved() {
		double[] vanilla = new double[ToolTier.values().length];
		vanilla[ToolTier.WOOD.ordinal()] = 2;
		vanilla[ToolTier.STONE.ordinal()] = 4;
		vanilla[ToolTier.COPPER.ordinal()] = 5;
		vanilla[ToolTier.GOLD.ordinal()] = 12;
		vanilla[ToolTier.IRON.ordinal()] = 6;
		vanilla[ToolTier.DIAMOND.ordinal()] = 8;
		vanilla[ToolTier.NETHERITE.ordinal()] = 9;
		for (ToolTier t : ToolTier.values()) assertEquals(vanilla[t.ordinal()] / 2, ToolRules.multiplier(t), 1e-12, t.name());
		assertEquals(1.5, ToolRules.multiplier(ToolTier.IRON) / ToolRules.multiplier(ToolTier.STONE), 1e-12);
		assertEquals(3.0, ToolRules.multiplier(ToolTier.IRON) / ToolRules.multiplier(ToolTier.WOOD), 1e-12);
	}

	/** The work constants are anchored on stone: a stone tool's real pace is what it was before the table changed. */
	@Test
	void stonePaceIsUnchanged() {
		double stone = ToolRules.multiplier(ToolTier.STONE);
		assertEquals(40.0, Balance.BASE_TICKS_PER_LOG / stone, 1e-9); // ticks per log
		assertEquals(2000.0, Balance.MINER_HAUL_UNITS / stone, 1e-9); // ticks per haul at level 1
	}
}
