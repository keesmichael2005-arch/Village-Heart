package dev.villageheart.core.lumber;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.tree.Species;
import org.junit.jupiter.api.Test;

class ReplantRulesTest {
	@Test
	void speciesMapToTheirSaplings() {
		assertEquals("minecraft:oak_sapling", ReplantRules.saplingFor(Species.OAK));
		assertEquals("minecraft:spruce_sapling", ReplantRules.saplingFor(Species.SPRUCE));
		assertEquals("minecraft:birch_sapling", ReplantRules.saplingFor(Species.BIRCH));
		assertEquals("minecraft:jungle_sapling", ReplantRules.saplingFor(Species.JUNGLE));
		assertEquals("minecraft:acacia_sapling", ReplantRules.saplingFor(Species.ACACIA));
		assertEquals("minecraft:dark_oak_sapling", ReplantRules.saplingFor(Species.DARK_OAK));
		assertEquals("minecraft:pale_oak_sapling", ReplantRules.saplingFor(Species.PALE_OAK));
		assertEquals("minecraft:cherry_sapling", ReplantRules.saplingFor(Species.CHERRY));
		assertEquals("minecraft:azalea", ReplantRules.saplingFor(Species.AZALEA));
		assertEquals("minecraft:mangrove_propagule", ReplantRules.saplingFor(Species.MANGROVE));
	}

	@Test
	void oneSaplingPerBaseCellAndFourForTwoByTwoSpecies() {
		assertEquals(1, ReplantRules.saplingsNeeded(Species.OAK, 1));
		assertEquals(4, ReplantRules.saplingsNeeded(Species.DARK_OAK, 4));
		assertEquals(4, ReplantRules.saplingsNeeded(Species.SPRUCE, 4));
		assertEquals(4, ReplantRules.saplingsNeeded(Species.PALE_OAK, 1));
		assertThrows(IllegalArgumentException.class, () -> ReplantRules.saplingsNeeded(Species.OAK, 0));
	}

	@Test
	void fallbackOnlyAfter6000TicksAndNeverForTwoByTwoSpecies() {
		assertFalse(ReplantRules.fallbackAllowed(5999, Species.OAK));
		assertTrue(ReplantRules.fallbackAllowed(6000, Species.OAK));
		assertFalse(ReplantRules.fallbackAllowed(60000, Species.DARK_OAK));
	}

	@Test
	void spotsExpireAfterThreeDays() {
		assertFalse(ReplantRules.expired(0, 3 * 24000 - 1));
		assertTrue(ReplantRules.expired(0, 3 * 24000));
		assertFalse(ReplantRules.expired(100, 100 + 3 * 24000 - 1));
	}

	@Test
	void keepIsWhatSpotsNeedPlusTheReserveButNeverMoreThanHeld() {
		assertEquals(5, ReplantRules.keepCount(6, 1));
		assertEquals(3, ReplantRules.keepCount(3, 1));
		assertEquals(4, ReplantRules.keepCount(10, 0));
		assertEquals(0, ReplantRules.keepCount(0, 2));
		assertEquals(8, ReplantRules.keepCount(64, 4));
	}
}
