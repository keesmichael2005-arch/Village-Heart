package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import org.junit.jupiter.api.Test;

class ClaimAreaTest {
	static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 64, 0);

	static HeartLocation at(int x, int y, int z) { return new HeartLocation("minecraft:overworld", x, y, z); }

	@Test void exactlyOnTheRadiusIsInside() {
		assertNull(ClaimArea.check(HEART, at(80, 64, 0), BuildingType.STORAGE));
		assertNull(ClaimArea.check(HEART, at(0, 64, -80), BuildingType.STORAGE));
		assertNull(ClaimArea.check(HEART, at(0, 144, 0), BuildingType.STORAGE), "the claim is a sphere, vertical counts");
	}

	@Test void oneBlockOutsideIsOutside() {
		Reason r = ClaimArea.check(HEART, at(81, 64, 0), BuildingType.STORAGE);
		assertEquals("Outside the village: 81 blocks from the Heart (max 80)", r.message());
	}

	@Test void sphereNotCube() {
		// 57,57 is 80.6 away: inside a 80-cube, outside the sphere; distance is rounded up
		Reason r = ClaimArea.check(HEART, at(57, 64, 57), BuildingType.HOUSE);
		assertEquals("Outside the village: 81 blocks from the Heart (max 80)", r.message());
		assertNull(ClaimArea.check(HEART, at(56, 64, 56), BuildingType.HOUSE), "79.2");
	}

	@Test void outdoorExceptionIsOnlyForTwoSites() {
		for (BuildingType t : new BuildingType[] {BuildingType.MINESHAFT, BuildingType.HUNTING_GROUND}) {
			assertNull(ClaimArea.check(HEART, at(128, 64, 0), t), t.toString());
			assertEquals("Outside the village: 129 blocks from the Heart (max 128)", ClaimArea.check(HEART, at(129, 64, 0), t).message());
		}
		assertNotNull(ClaimArea.check(HEART, at(100, 64, 0), BuildingType.TRAINING_YARD));
		assertNotNull(ClaimArea.check(HEART, at(100, 64, 0), BuildingType.MINE));
	}

	@Test void otherDimension() {
		Reason r = ClaimArea.check(HEART, new HeartLocation("minecraft:the_nether", 0, 64, 0), BuildingType.STORAGE);
		assertEquals(Reason.Code.OTHER_DIMENSION, r.code());
		assertEquals("Not in the village's dimension", r.message());
	}

	@Test void hugeDistancesDoNotOverflow() {
		assertEquals("Outside the village: 100000 blocks from the Heart (max 80)",
				ClaimArea.check(HEART, at(0, 64, 100000), BuildingType.STORAGE).message());
		assertNotNull(ClaimArea.check(HEART, at(Integer.MAX_VALUE, 64, Integer.MIN_VALUE), BuildingType.STORAGE));
	}
}
