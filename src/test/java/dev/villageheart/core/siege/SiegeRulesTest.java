package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.siege.ThreatRules.Gates;
import org.junit.jupiter.api.Test;

/**
 * T-L1-21. Spawn distance is {@code max(outermost, 32) + 12} clamped to [32, 96], so an outermost cell at 10 spawns at 44.
 * The test table's shorter wording ("distance + 12, then clamp") would have put that spawn at 32.
 */
class SiegeRulesTest {
	@Test
	void chanceSizeAndTheGates() {
		assertEquals(0.04, ThreatRules.siegeChance(0), 1e-9);
		assertEquals(0.20, ThreatRules.siegeChance(8), 1e-9);
		assertEquals(4, SiegeSize.zombies(0, 4));
		assertEquals(10, SiegeSize.zombies(3, 4));
		assertEquals(20, SiegeSize.zombies(10, 4));
		assertEquals(3, SiegeSize.zombies(8, 2));
		assertEquals(2, SiegeSize.zombies(0, 0));
		assertEquals(2, SiegeSize.zombies(0, 1));
		assertEquals(4, SiegeSize.zombies(0, 3));

		assertFalse(ThreatRules.mayRollSiege(Tier.HAMLET, 20, ThreatState.NO_DAY, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollSiege(Tier.VILLAGE, 4, ThreatState.NO_DAY, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertTrue(ThreatRules.mayRollSiege(Tier.VILLAGE, 5, ThreatState.NO_DAY, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, 2, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertTrue(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, 3, ThreatState.NO_DAY, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, ThreatState.NO_DAY, 1, false, false, Gates.OPEN));
		assertTrue(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, ThreatState.NO_DAY, 2, false, false, Gates.OPEN));
		assertFalse(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, ThreatState.NO_DAY, ThreatState.NO_DAY, false, false, new Gates(true, true, true, true, true)));
		assertFalse(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, ThreatState.NO_DAY, ThreatState.NO_DAY, false, false, new Gates(false, true, true, false, true)));
		assertFalse(ThreatRules.mayRollSiege(Tier.VILLAGE, 20, ThreatState.NO_DAY, ThreatState.NO_DAY, false, false, new Gates(false, true, true, true, false)));
	}

	@Test
	void spawnDistanceUsesTheFloorBeforeThePad() {
		assertEquals(44, ThreatRules.spawnDistance(10));
		assertEquals(44, ThreatRules.spawnDistance(0));
		assertEquals(52, ThreatRules.spawnDistance(40));
		assertEquals(96, ThreatRules.spawnDistance(90));
		assertEquals(96, ThreatRules.spawnDistance(200));
	}
}
