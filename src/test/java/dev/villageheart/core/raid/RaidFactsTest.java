package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The claim-edge half of T-L1-6b. */
class RaidFactsTest {
	@Test void claimEdgeCountsARaiderAt79AndNotAt81() {
		HeartLocation heart = new HeartLocation("minecraft:overworld", 0, 64, 0);
		RaidFacts inside = RaidFacts.from(1, false, false, false, false, new Pos(85, 64, 0), List.of(new Pos(79, 64, 0)), heart,
				1, 0, 1, false, false);
		assertEquals(1, inside.raidersInClaim());
		assertFalse(inside.centreInClaim());
		assertTrue(inside.relevant());
		RaidFacts outside = RaidFacts.from(1, false, false, false, false, new Pos(85, 64, 0), List.of(new Pos(81, 64, 0)), heart,
				1, 0, 1, false, false);
		assertEquals(0, outside.raidersInClaim());
		assertFalse(outside.relevant());
	}
}
