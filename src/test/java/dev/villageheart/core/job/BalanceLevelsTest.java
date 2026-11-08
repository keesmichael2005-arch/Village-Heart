package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class BalanceLevelsTest {
	@Test
	void everyWorkUnitHasPositiveXp() {
		assertEquals(WorkUnit.values().length, Balance.workUnitXpEntries());
		for (WorkUnit u : WorkUnit.values()) assertTrue(Balance.milliXpPerUnit(u) > 0, u.name());
		assertEquals(200, Balance.milliXpPerUnit(WorkUnit.BLOCK_PLACED));
		assertEquals(1000, Balance.milliXpPerUnit(WorkUnit.LOG_FELLED));
		assertEquals(1000, Balance.milliXpPerUnit(WorkUnit.CROP_HARVESTED));
	}

	@Test
	void designNumbers() {
		assertEquals(Balance.LEVEL_3_XP, Balance.MAX_BANKED_XP);
		assertArrayEquals(new double[] {1.0, 1.25, 1.5}, Balance.LEVEL_SPEED);
		assertArrayEquals(new int[] {1, 2, 3}, Balance.LEVEL_CAPS);
		assertEquals(100, Balance.LEVEL_2_XP);
		assertEquals(300, Balance.LEVEL_3_XP);
		assertEquals(1.0, Balance.TOOL_SPEED[ToolTier.WOOD.ordinal()]);
		assertEquals(2.0, Balance.TOOL_SPEED[ToolTier.STONE.ordinal()]);
		assertEquals(3.0, Balance.TOOL_SPEED[ToolTier.IRON.ordinal()]);
		assertEquals(4.0, Balance.TOOL_SPEED[ToolTier.DIAMOND.ordinal()]);
	}
}
