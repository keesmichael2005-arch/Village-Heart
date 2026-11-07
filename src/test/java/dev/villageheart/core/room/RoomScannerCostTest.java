package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-16b / T-L1-17: the scan cost in view calls, which (unlike wall-clock time) does not depend on machine load. */
class RoomScannerCostTest {
	private static final Cell STONE = Cell.solid("minecraft:stone");

	private static final class CountingView implements BlockView {
		final BlockView inner;
		int cells, sky;
		CountingView(BlockView inner) { this.inner = inner; }
		@Override public Cell cell(int x, int y, int z) { cells++; return inner.cell(x, y, z); }
		@Override public boolean openToSky(int x, int y, int z) { sky++; return inner.openToSky(x, y, z); }
	}

	/** Each neighbour is read once (air + shell cells) and floor area reads one cell per column bottom. */
	@Test
	void room16x16x8ReadsEachCellAboutOnce() {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 17, 9, 17, STONE).door(8, 1, 0);
		CountingView v = new CountingView(g);
		RoomScan s = RoomScanner.scanFromMarker(v, new Pos(8, 3, -1), Dir.NORTH);
		assertTrue(s.valid());
		assertEquals(2048, s.volume());
		assertTrue(v.cells <= 2 * 2048 + 256, "cell() calls " + v.cells);
		assertTrue(v.sky <= 2048 + 16, "openToSky() calls " + v.sky);
	}

	/** The cap is enforced during the fill: an 8000-air cave stops after 4097 cells and does not read the rest. */
	@Test
	void capIsEnforcedDuringTheFill() {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 21, 21, 21, STONE).door(8, 1, 0);
		CountingView v = new CountingView(g);
		RoomScan s = RoomScanner.scanFromMarker(v, new Pos(8, 3, -1), Dir.NORTH);
		assertEquals(4097, s.volume());
		assertEquals(RoomProblem.Kind.TOO_BIG, s.problems().get(0).kind());
		assertTrue(v.cells <= 2 * 4097, "cell() calls " + v.cells);
		assertTrue(v.sky <= 4097 + 16, "openToSky() calls " + v.sky);
	}
}
