package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-13 */
class DirTest {
	@Test
	void namesAndOrderMatchMinecraftDirection() {
		// the glue maps with Dir.valueOf(direction.name()); the fill's neighbour order relies on this order
		assertEquals(List.of("DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"), Arrays.stream(Dir.values()).map(Enum::name).toList());
	}

	@Test
	void vectors() {
		assertEquals(-1, Dir.NORTH.dz);
		assertEquals(1, Dir.SOUTH.dz);
		assertEquals(-1, Dir.WEST.dx);
		assertEquals(1, Dir.EAST.dx);
		assertEquals(-1, Dir.DOWN.dy);
		assertEquals(1, Dir.UP.dy);
		for (Dir d : Dir.values()) assertEquals(1, Math.abs(d.dx) + Math.abs(d.dy) + Math.abs(d.dz));
	}

	@Test
	void rotations() {
		for (Dir d : Dir.values()) assertEquals(d, d.opposite().opposite());
		assertEquals(Dir.EAST, Dir.NORTH.clockwise());
		assertEquals(Dir.SOUTH, Dir.EAST.clockwise());
		assertEquals(Dir.WEST, Dir.SOUTH.clockwise());
		assertEquals(Dir.NORTH, Dir.WEST.clockwise());
		assertEquals(Dir.WEST, Dir.NORTH.counterClockwise());
		assertEquals(Dir.NORTH, Dir.EAST.counterClockwise());
		assertTrue(Dir.NORTH.horizontal());
		assertFalse(Dir.UP.horizontal());
		assertThrows(IllegalStateException.class, Dir.UP::clockwise);
		assertThrows(IllegalStateException.class, Dir.DOWN::counterClockwise);
	}
}
