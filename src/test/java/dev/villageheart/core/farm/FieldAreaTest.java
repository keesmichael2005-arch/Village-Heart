package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import org.junit.jupiter.api.Test;

class FieldAreaTest {
	private static final Pos JB = new Pos(100, 64, 200);

	private static boolean in(int dx, int dy, int dz) { return FieldArea.contains(JB, new Pos(100 + dx, 64 + dy, 200 + dz)); }

	@Test
	void theRadiusIsAnInclusiveDisc() {
		assertTrue(in(24, 0, 0));
		assertTrue(in(-24, 0, 0));
		assertTrue(in(0, 0, 24));
		assertFalse(in(25, 0, 0));
		assertFalse(in(17, 0, 17), "24.04 is outside, though inside the bounding box");
		assertTrue(in(16, 0, 17), "23.35 is inside");
		assertTrue(in(0, 0, 0));
	}

	@Test
	void heightIsLimitedToEight() {
		assertTrue(in(0, 8, 0));
		assertFalse(in(0, 9, 0));
		assertTrue(in(0, -8, 0));
		assertFalse(in(0, -9, 0));
	}
}
