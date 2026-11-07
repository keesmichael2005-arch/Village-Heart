package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-12 */
class PosTest {
	@Test
	void packUnpackRoundTrip() {
		int[] xs = {-33554432, -30000000, -1, 0, 1, 29999999, 33554431};
		int[] ys = {-2048, -64, -1, 0, 70, 319, 2047};
		for (int x : xs) for (int y : ys) for (int z : xs) {
			Pos p = new Pos(x, y, z);
			assertEquals(p, Pos.unpack(p.pack()), p.toString());
		}
	}

	@Test
	void toStringFormat() {
		assertEquals("(12, 70, -4)", new Pos(12, 70, -4).toString());
	}

	@Test
	void offsets() {
		Pos p = new Pos(5, 5, 5);
		assertEquals(new Pos(5, 4, 5), p.offset(Dir.DOWN));
		assertEquals(new Pos(5, 6, 5), p.offset(Dir.UP));
		assertEquals(new Pos(5, 5, 4), p.offset(Dir.NORTH));
		assertEquals(new Pos(5, 5, 6), p.offset(Dir.SOUTH));
		assertEquals(new Pos(4, 5, 5), p.offset(Dir.WEST));
		assertEquals(new Pos(6, 5, 5), p.offset(Dir.EAST));
		assertEquals(p.offset(Dir.UP), p.above());
		assertEquals(p.offset(Dir.DOWN), p.below());
	}

	@Test
	void ordering() {
		assertTrue(new Pos(0, 9, 9).compareTo(new Pos(1, 0, 0)) < 0);
		assertTrue(new Pos(1, 0, 9).compareTo(new Pos(1, 1, 0)) < 0);
		assertTrue(new Pos(1, 1, 0).compareTo(new Pos(1, 1, 1)) < 0);
		assertEquals(0, new Pos(1, 1, 1).compareTo(new Pos(1, 1, 1)));
	}
}
