package dev.villageheart.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ClaimGeometryTest {
	static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 64, 0);

	static HeartLocation at(int dx, int dy, int dz) { return new HeartLocation("minecraft:overworld", dx, 64 + dy, dz); }

	@Test
	void sphereNotBox() {
		assertTrue(ClaimGeometry.inClaim(HEART, at(80, 0, 0)));
		assertFalse(ClaimGeometry.inClaim(HEART, at(81, 0, 0)));
		assertTrue(ClaimGeometry.inClaim(HEART, at(0, 80, 0)));
		assertFalse(ClaimGeometry.inClaim(HEART, at(0, 0, -81)));
		// a box corner (60, 60) is inside 80 on every axis but 84.9 blocks away
		assertFalse(ClaimGeometry.inClaim(HEART, at(60, 0, 60)));
		assertTrue(ClaimGeometry.inClaim(HEART, at(56, 0, 56)));
		assertTrue(ClaimGeometry.inClaim(HEART, at(0, 0, 0)));
	}

	@Test
	void otherDimensionIsOut() {
		assertFalse(ClaimGeometry.inClaim(HEART, new HeartLocation("minecraft:the_nether", 0, 64, 0)));
	}
}
