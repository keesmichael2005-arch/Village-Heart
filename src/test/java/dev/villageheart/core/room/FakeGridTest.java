package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-15: the fixture the other room tests stand on. */
class FakeGridTest {
	@Test
	void unsetIsOpenAndSkyFollowsCells() {
		FakeGrid g = new FakeGrid();
		assertEquals(Cell.OPEN, g.cell(5, 5, 5));
		assertTrue(g.openToSky(5, 5, 5));

		g.set(5, 10, 5, Cell.SOLID);
		assertFalse(g.openToSky(5, 5, 5), "under a roof");
		assertTrue(g.openToSky(5, 11, 5), "above the roof");
		assertTrue(g.openToSky(6, 5, 5), "next column");

		g.clear(5, 10, 5);
		assertTrue(g.openToSky(5, 5, 5), "roof cleared");

		g.box(0, 3, 0, 9, 3, 9, Cell.SOLID);
		assertFalse(g.openToSky(5, 1, 5), "box re-covers");
		g.clear(5, 3, 5);
		assertTrue(g.openToSky(5, 1, 5));
		g.hollow(0, 0, 0, 4, 4, 4, Cell.SOLID);
		assertFalse(g.openToSky(2, 1, 2), "hollow re-covers");
		assertTrue(g.openToSky(2, 5, 2));
	}

	@Test
	void topFallsToTheNextSolidBelow() {
		FakeGrid g = new FakeGrid();
		g.set(0, 5, 0, Cell.SOLID).set(0, 9, 0, Cell.SOLID);
		g.clear(0, 9, 0);
		assertFalse(g.openToSky(0, 3, 0));
		assertTrue(g.openToSky(0, 6, 0));
	}

	@Test
	void openCellsWithContentsDoNotBlockTheSky() {
		FakeGrid g = new FakeGrid();
		g.set(1, 5, 1, Cell.open("minecraft:torch"));
		assertEquals(CellKind.OPEN, g.cell(1, 5, 1).kind());
		assertTrue(g.openToSky(1, 2, 1));
	}

	@Test
	void unloadedBeyondLimit() {
		FakeGrid g = new FakeGrid().unloadedBeyondX(3);
		assertEquals(Cell.UNLOADED, g.cell(4, 0, 0));
		assertEquals(Cell.OPEN, g.cell(3, 0, 0));
	}
}
