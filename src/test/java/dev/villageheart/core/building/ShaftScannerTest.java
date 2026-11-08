package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.building.ShaftScanner.ShaftSurvey;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.FakeGrid;
import dev.villageheart.core.room.Pos;
import org.junit.jupiter.api.Test;

class ShaftScannerTest {
	static final Cell STONE = Cell.solid("minecraft:stone");
	static final Cell RAIL = Cell.open("minecraft:rail", "#rail");
	static final Cell TORCH = Cell.open("minecraft:torch", "#light");
	static final Pos MARKER = new Pos(4, 21, 4);

	/**
	 * Solid ground x,z 0..40, y 0..20; a 1x1 hole at (4, *, 4) from the surface down to a 3x3x2 chamber (x,z 3..5) whose lowest
	 * layer is at y = 21 - depth; so the survey depth is exactly {@code depth}. The marker cell (4, 21, 4) is above the hole.
	 */
	static FakeGrid shaft(int depth, int rails, boolean torch) {
		FakeGrid g = new FakeGrid();
		g.box(0, 0, 0, 40, 20, 40, STONE);
		int fy = 21 - depth;
		for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++) { g.clear(x, fy, z); g.clear(x, fy + 1, z); }
		for (int y = fy + 2; y <= 20; y++) g.clear(4, y, 4);
		int placed = 0;
		for (int x = 3; x <= 5; x++) {
			for (int z = 3; z <= 5; z++) {
				if (placed < rails) g.set(x, fy, z, RAIL);
				else if (placed == rails && torch) g.set(x, fy, z, TORCH);
				placed++;
			}
		}
		g.set(4, 21, 3, Cell.solid("minecraft:oak_fence"));
		return g;
	}

	@Test void boundaryShaftIsDepthTwelve() {
		ShaftSurvey s = ShaftScanner.scan(shaft(12, 8, true), MARKER);
		assertEquals(12, s.depth());
		assertEquals(9, s.deepestY());
		assertEquals(8, s.rails());
		assertEquals(1, s.lights());
		assertNull(s.notLoaded());
	}

	@Test void shallowerAndFewerRails() {
		assertEquals(11, ShaftScanner.scan(shaft(11, 8, true), MARKER).depth());
		assertEquals(6, ShaftScanner.scan(shaft(12, 6, true), MARKER).rails());
		assertEquals(0, ShaftScanner.scan(shaft(12, 8, false), MARKER).lights());
	}

	@Test void glowstoneInTheWallCounts() {
		FakeGrid g = shaft(12, 8, false);
		g.set(2, 9, 4, Cell.solid("minecraft:glowstone", "#light"));
		assertEquals(1, ShaftScanner.scan(g, MARKER).lights());
	}

	@Test void surfaceDoesNotSpread() {
		FakeGrid g = shaft(12, 8, true);
		g.set(7, 21, 4, TORCH);
		for (int z = 5; z <= 8; z++) g.set(7, 21, z, RAIL);
		ShaftSurvey s = ShaftScanner.scan(g, MARKER);
		assertEquals(8, s.rails());
		assertEquals(1, s.lights());
	}

	@Test void sideTunnelCountsWithinTheRadius() {
		FakeGrid g = shaft(12, 8, true);
		int fy = 9;
		for (int x = 6; x <= 25; x++) { g.clear(x, fy, 4); g.clear(x, fy + 1, 4); }
		g.set(10, fy, 4, RAIL);
		g.set(24, fy, 4, RAIL); // x distance 20 > 16
		ShaftSurvey s = ShaftScanner.scan(g, MARKER);
		assertEquals(9, s.rails(), "the rail at x=10 is visited, the one at x=24 is not");
	}

	@Test void hugeCaveStopsAtTheCap() {
		FakeGrid g = new FakeGrid();
		g.box(0, 0, 0, 40, 60, 40, STONE);
		for (int x = 5; x <= 34; x++) for (int y = 20; y <= 49; y++) for (int z = 5; z <= 34; z++) g.clear(x, y, z);
		for (int y = 50; y <= 60; y++) g.clear(20, y, 20);
		Pos marker = new Pos(20, 61, 20);
		ShaftSurvey s = ShaftScanner.scan(g, marker);
		assertNull(s.notLoaded());
		assertTrue(s.depth() >= 12 && s.depth() <= 41, "depth " + s.depth());
		assertEquals(s, ShaftScanner.scan(g, marker), "deterministic");
	}

	@Test void unloadedCellStopsTheSurvey() {
		FakeGrid g = shaft(12, 8, true).unloadedBeyondX(4);
		ShaftSurvey s = ShaftScanner.scan(g, MARKER);
		assertNotNull(s.notLoaded());
		assertTrue(s.notLoaded().x() > 4);
	}

	private static void assertDeepestCellIsOpenAtDeepestY(FakeGrid g, ShaftSurvey s) {
		assertEquals(s.deepestY(), s.deepestCell().y(), "the deepest cell is at the deepest y");
		assertNotEquals(dev.villageheart.core.room.CellKind.SOLID, g.cell(s.deepestCell()).kind(), "and open: " + s.deepestCell());
		assertEquals(s.deepestY(), s.shaftDepth().deepestY());
		assertEquals(s.deepestCell(), s.shaftDepth().deepestCell());
	}

	@Test void deepestCellOfAStraightShaft() {
		FakeGrid g = shaft(12, 8, true);
		ShaftSurvey s = ShaftScanner.scan(g, MARKER);
		assertDeepestCellIsOpenAtDeepestY(g, s);
		assertEquals(9, s.deepestCell().y());
		assertTrue(s.deepestCell().x() >= 3 && s.deepestCell().x() <= 5 && s.deepestCell().z() >= 3 && s.deepestCell().z() <= 5, "in the chamber: " + s.deepestCell());
	}

	@Test void deepestCellOfAnLShapedShaft() {
		FakeGrid g = shaft(12, 8, true);
		// a connection at x = 6 and a tunnel two blocks lower than the chamber, running east
		for (int y = 5; y <= 10; y++) g.clear(6, y, 4);
		for (int x = 7; x <= 14; x++) { g.clear(x, 5, 4); g.clear(x, 6, 4); }
		ShaftSurvey s = ShaftScanner.scan(g, MARKER);
		assertEquals(5, s.deepestY());
		assertDeepestCellIsOpenAtDeepestY(g, s);
		assertEquals(16, s.depth());
	}

	@Test void deepestCellWhenTheBottomIsBesideTheMarkerColumn() {
		FakeGrid g = new FakeGrid();
		g.box(0, 0, 0, 40, 20, 40, STONE);
		for (int y = 8; y <= 20; y++) g.clear(4, y, 4);   // the post column down to y 8
		for (int y = 8; y <= 9; y++) for (int x = 5; x <= 7; x++) g.clear(x, y, 4); // the chamber to its east
		for (int x = 5; x <= 7; x++) g.set(x, 8, 4, RAIL);
		ShaftSurvey s = ShaftScanner.scan(g, MARKER);
		assertEquals(8, s.deepestY());
		assertDeepestCellIsOpenAtDeepestY(g, s);
	}

	@Test void deepestCellIsTheSameInEveryScan() {
		FakeGrid g = shaft(14, 8, true);
		assertEquals(ShaftScanner.scan(g, MARKER).deepestCell(), ShaftScanner.scan(g, MARKER).deepestCell());
	}

	@Test void twoScansAreEqual() {
		assertEquals(ShaftScanner.scan(shaft(12, 8, true), MARKER), ShaftScanner.scan(shaft(12, 8, true), MARKER));
	}
}
