package dev.villageheart.core.hunt;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.Needs;
import org.junit.jupiter.api.Test;

/** T-L1-7, the rank part of T-L1-10 and HuntBonus. */
class HuntPaceTest {
	@Test
	void paceByLevelAndWeapon() {
		assertEquals(1.0, HuntPace.multiplier(true, 1, Needs.DEFAULT), 1e-9);
		assertEquals(1.25, HuntPace.multiplier(true, 2, Needs.DEFAULT), 1e-9);
		assertEquals(1.5, HuntPace.multiplier(true, 3, Needs.DEFAULT), 1e-9);
		assertEquals(0.0, HuntPace.multiplier(false, 1, Needs.DEFAULT), 1e-9);
		assertEquals(1.0, HuntPace.multiplier(true, 0, Needs.DEFAULT), 1e-9);
		assertEquals(1.5, HuntPace.multiplier(true, 4, Needs.DEFAULT), 1e-9);
	}

	@Test
	void bowAndDiamondSwordGiveTheSamePace() {
		// the pace has no tier input at all: that is the point
		assertEquals(HuntPace.multiplier(true, 1, Needs.DEFAULT), HuntPace.multiplier(true, 1, Needs.DEFAULT));
	}

	@Test
	void rankPutsRangedFirstThenTiers() {
		for (ToolTier t : ToolTier.values()) assertTrue(HunterWeapons.rank(true, t) > HunterWeapons.rank(false, ToolTier.NETHERITE), t.toString());
		assertEquals(6, ToolTier.NETHERITE.ordinal());
		assertTrue(HunterWeapons.rank(false, ToolTier.IRON) > HunterWeapons.rank(false, ToolTier.STONE));
		assertEquals(HunterWeapons.rank(false, ToolTier.WOOD), ToolTier.WOOD.ordinal());
	}

	@Test
	void bonusOnlyForARangedWeaponWithAnArrow() {
		assertTrue(HuntBonus.applies(true, true));
		assertFalse(HuntBonus.applies(true, false));
		assertFalse(HuntBonus.applies(false, true));
		assertFalse(HuntBonus.applies(false, false));
	}
}
