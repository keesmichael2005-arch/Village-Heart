package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.RoomProblem.Kind;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoomScannerTest {
	private static final Cell STONE = Cell.solid("minecraft:stone");

	private static RoomScan scan(FakeGrid g) { return RoomScanner.scanFromMarker(g, FakeGrid.MARKER, Dir.NORTH); }

	private static void assertProblem(RoomScan s, Kind kind, Pos pos) {
		assertFalse(s.enclosed(), "enclosed");
		assertFalse(s.valid());
		assertEquals(List.of(new RoomProblem(kind, pos)), s.problems(), () -> "problems " + s.problems());
	}

	/** T-L1-1 */
	@Test
	void closedRoomIsEnclosed() {
		RoomScan s = scan(FakeGrid.room5x5x3());
		assertTrue(s.enclosed());
		assertTrue(s.valid());
		assertTrue(s.problems().isEmpty());
		assertEquals(75, s.volume());
		assertEquals(25, s.floorArea());
		assertEquals(new Pos(3, 1, 0), s.door());
		assertEquals(new Pos(3, 1, 1), s.start());
		assertEquals(FakeGrid.MARKER, s.marker());
		assertEquals(1, s.count("#door"));
		assertEquals(1, s.count("minecraft:oak_door"));
		assertTrue(s.contains(new Pos(3, 1, 1)));
		assertFalse(s.contains(new Pos(3, 1, 0)));
		assertTrue(s.occupies(new Pos(3, 1, 0)));
		assertFalse(s.occupies(new Pos(3, 1, -1)));
	}

	/** T-L1-2: every roof cell, door in the middle and in a corner. Exact cells: BFS order is fixed by Dir.values(). */
	@Test
	void missingRoofBlockReportsIt() {
		for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
			FakeGrid g = FakeGrid.room5x5x3();
			g.clear(x, 4, z);
			RoomScan s = scan(g);
			assertProblem(s, Kind.ROOF_MISSING, new Pos(x, 4, z));
			assertEquals("Roof missing at (" + x + ", 4, " + z + ")", s.problems().get(0).message());
		}
		for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
			FakeGrid g = new FakeGrid().hollow(0, 0, 0, 6, 4, 6, STONE).door(1, 1, 0);
			g.clear(x, 4, z);
			assertProblem(RoomScanner.scanFromMarker(g, new Pos(1, 3, -1), Dir.NORTH), Kind.ROOF_MISSING, new Pos(x, 4, z));
		}
	}

	@Test
	void largerRoofHolesReportAHoleCell() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.clear(3, 4, 3).clear(4, 4, 3);
		RoomScan s = scan(g);
		assertFalse(s.enclosed());
		assertEquals(Kind.ROOF_MISSING, s.problems().get(0).kind());
		assertTrue(List.of(new Pos(3, 4, 3), new Pos(4, 4, 3)).contains(s.problems().get(0).pos()), s.problems().toString());

		g = FakeGrid.room5x5x3();
		g.clear(3, 4, 3).clear(4, 4, 3).clear(3, 4, 4).clear(4, 4, 4);
		s = scan(g);
		assertEquals(Kind.ROOF_MISSING, s.problems().get(0).kind());
		assertTrue(List.of(new Pos(3, 4, 3), new Pos(4, 4, 3), new Pos(3, 4, 4), new Pos(4, 4, 4)).contains(s.problems().get(0).pos()),
				s.problems().toString());
	}

	@Test
	void roofEdgeAndCornerCellsDoNotMatter() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.clear(0, 4, 3);
		assertTrue(scan(g).valid(), "wall-top cell above a wall");
		g.clear(0, 4, 0);
		assertTrue(scan(g).valid(), "corner");
	}

	@Test
	void gableRoofSlopeCellReportsIt() {
		// ridge along z; roof height steps up towards x = 3
		FakeGrid g = new FakeGrid();
		for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
			int top = 4 + Math.min(x, 6 - x);
			g.set(x, top, z, STONE);
			g.set(x, 0, z, STONE);
			if (x == 0 || x == 6 || z == 0 || z == 6) for (int y = 1; y < top; y++) g.set(x, y, z, STONE);
		}
		g.set(3, 1, 0, Cell.door(DoorPart.LOWER, "minecraft:oak_door", RoomContents.DOOR));
		g.set(3, 2, 0, Cell.door(DoorPart.UPPER).withPartner(Dir.DOWN));
		assertTrue(scan(g).valid(), "closed gable");
		g.clear(2, 6, 3);
		assertProblem(scan(g), Kind.ROOF_MISSING, new Pos(2, 6, 3));
	}

	@Test
	void solidStandInInTheHoleIsValid() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.clear(2, 4, 4);
		assertFalse(scan(g).valid());
		g.set(2, 4, 4, Cell.solid("minecraft:white_carpet"));
		assertTrue(scan(g).valid());
	}

	/** T-L1-3 */
	@Test
	void missingWallBlockReportsIt() {
		List<Pos> walls = new ArrayList<>();
		for (int y = 1; y <= 3; y++) for (int i = 1; i <= 5; i++) {
			walls.add(new Pos(0, y, i));
			walls.add(new Pos(6, y, i));
			walls.add(new Pos(i, y, 6));
			if (i != 3 || y == 3) walls.add(new Pos(i, y, 0));
		}
		for (Pos w : walls) {
			FakeGrid g = FakeGrid.room5x5x3();
			g.clear(w.x(), w.y(), w.z());
			RoomScan s = scan(g);
			assertProblem(s, Kind.WALL_GAP, w);
			assertEquals("Wall gap at " + w, s.problems().get(0).message());
		}
		FakeGrid g = FakeGrid.room5x5x3();
		g.clear(0, 2, 0);
		assertTrue(scan(g).valid(), "corner column cell");
	}

	@Test
	void emptyDoorwayReportsTheCellUsed() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.clear(5, 1, 6).clear(5, 2, 6);
		assertProblem(scan(g), Kind.WALL_GAP, new Pos(5, 1, 6));
	}

	@Test
	void gapOntoAPorchReportsTheGapNotThePorchEdge() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.box(-6, 0, -6, 12, 0, 12, STONE);
		g.box(-3, 4, 0, -1, 4, 6, STONE);
		g.clear(0, 2, 3);
		assertProblem(scan(g), Kind.WALL_GAP, new Pos(0, 2, 3));
	}

	@Test
	void missingWallTopUnderAnInsetRoof() {
		FakeGrid g = FakeGrid.room5x5x3();
		for (int i = 0; i <= 6; i++) for (int j = 0; j <= 6; j++) if (i == 0 || i == 6 || j == 0 || j == 6) g.clear(i, 4, j);
		assertTrue(scan(g).valid(), "inset roof is closed");
		g.clear(0, 3, 3);
		assertProblem(scan(g), Kind.WALL_GAP, new Pos(0, 3, 3));
	}

	/** T-L1-4 */
	@Test
	void tooBigFails() {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 17, 17, 17, STONE).door(8, 1, 0);
		Pos marker = new Pos(8, 3, -1);
		RoomScan ok = RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		assertTrue(ok.enclosed(), ok.problems().toString());
		assertEquals(4096, ok.volume());

		g.set(-1, 5, 5, STONE).clear(0, 5, 5); // an alcove in the west wall: one more air cell, still closed
		RoomScan big = RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		assertProblem(big, Kind.TOO_BIG, null);
		assertEquals("Room too big (max 4096 air)", big.problems().get(0).message());
		assertEquals(4097, big.volume());
	}

	@Test
	void tooBigUndergroundHasNoSkyReason() {
		FakeGrid g = new FakeGrid().box(0, 0, 0, 39, 39, 39, STONE);
		carve(g, 5, 5, 5, 14, 9, 14);
		carve(g, 18, 5, 5, 37, 24, 24);
		carve(g, 15, 7, 10, 17, 7, 10);
		g.door(9, 5, 4);
		RoomScan s = RoomScanner.scanFromMarker(g, new Pos(9, 7, 3), Dir.NORTH);
		assertProblem(s, Kind.TOO_BIG, null);
	}

	private static void carve(FakeGrid g, int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) g.clear(x, y, z);
	}

	/** T-L1-6: the core treats every SOLID cell, door halves included, as wall. */
	@Test
	void wallLikeCellsBlock() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.set(0, 2, 3, Cell.solid("minecraft:oak_fence"));
		g.set(0, 2, 4, Cell.solid("minecraft:glass"));
		g.set(0, 2, 2, Cell.solid("minecraft:glass_pane"));
		g.door(6, 1, 3);
		RoomScan s = scan(g);
		assertTrue(s.valid(), s.problems().toString());
		assertEquals(2, s.count("#door"));
	}

	/** T-L1-7 */
	@Test
	void floorArea() {
		FakeGrid l = FakeGrid.room5x5x3();
		l.box(4, 1, 4, 5, 3, 5, STONE);
		assertEquals(21, scan(l).floorArea());

		FakeGrid bed = FakeGrid.room5x5x3();
		bed.set(1, 1, 1, Cell.solid("minecraft:red_bed", "#bed")).set(2, 1, 1, Cell.solid("minecraft:red_bed", "#bed"));
		assertEquals(25, scan(bed).floorArea());

		FakeGrid mezz = new FakeGrid().hollow(0, 0, 0, 6, 6, 6, STONE).door(3, 1, 0);
		mezz.box(1, 3, 1, 2, 3, 5, STONE);
		RoomScan ms = scan(mezz);
		assertTrue(ms.valid(), ms.problems().toString());
		assertEquals(25, ms.floorArea());

		FakeGrid column = FakeGrid.room5x5x3();
		column.box(1, 1, 1, 1, 3, 1, STONE);
		assertEquals(24, scan(column).floorArea());
	}

	/** T-L1-8 */
	@Test
	void contentsFoundInShellAndInterior() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.set(1, 1, 1, Cell.solid("minecraft:chest", "#chest"));
		g.set(5, 1, 5, Cell.open("minecraft:torch", "#light"));
		g.set(0, 2, 3, Cell.solid("minecraft:barrel"));
		g.set(-1, 1, 3, Cell.solid("minecraft:chest", "#chest"));
		RoomScan s = scan(g);
		assertTrue(s.valid(), s.problems().toString());
		assertEquals(1, s.count("#chest"));
		assertEquals(List.of(new Pos(1, 1, 1)), s.positions("#chest"));
		assertEquals(1, s.count("minecraft:barrel"));
		assertEquals(1, s.count("#light"));
		assertEquals(1, s.count("minecraft:chest"));
		assertEquals(74, s.volume());
		assertEquals(List.of("#chest", "#door", "#light", "minecraft:barrel", "minecraft:chest", "minecraft:oak_door", "minecraft:stone", "minecraft:torch"),
				new ArrayList<>(s.contents().keySet()));
	}

	/** T-L1-9 */
	@Test
	void notLoadedFails() {
		RoomScan s = scan(FakeGrid.room5x5x3().unloadedBeyondX(4));
		assertFalse(s.enclosed());
		assertEquals(1, s.problems().size());
		assertEquals(Kind.NOT_LOADED, s.problems().get(0).kind());
		assertEquals(5, s.problems().get(0).pos().x());
		assertTrue(s.problems().get(0).message().startsWith("Room reaches an unloaded area at (5, "));

		assertProblem(scan(FakeGrid.room5x5x3().unloadedBeyondX(2)), Kind.NOT_LOADED, new Pos(3, 2, 0));
	}

	/** T-L1-10 */
	@Test
	void doorSearch() {
		Pos door = new Pos(3, 1, 0);
		for (Pos m : List.of(new Pos(3, 3, -1), new Pos(2, 2, -1), new Pos(4, 2, -1), new Pos(2, 1, -1), new Pos(4, 1, -1), new Pos(2, 3, -1),
				new Pos(4, 3, -1))) {
			RoomScan s = RoomScanner.scanFromMarker(FakeGrid.room5x5x3(), m, Dir.NORTH);
			assertEquals(door, s.door(), "marker " + m);
			assertTrue(s.valid(), "marker " + m + " " + s.problems());
		}
		RoomScan far = RoomScanner.scanFromMarker(FakeGrid.room5x5x3(), new Pos(1, 3, -1), Dir.NORTH);
		assertProblem(far, Kind.NO_DOOR, new Pos(1, 3, -1));
		assertNull(far.door());
		assertEquals(0, far.volume());

		FakeGrid upperOnly = FakeGrid.room5x5x3();
		upperOnly.set(3, 1, 0, STONE);
		assertProblem(scan(upperOnly), Kind.NO_DOOR, FakeGrid.MARKER);

		FakeGrid blocked = FakeGrid.room5x5x3();
		blocked.set(3, 1, 1, STONE).set(3, 2, 1, STONE);
		assertProblem(scan(blocked), Kind.DOORWAY_BLOCKED, new Pos(3, 1, 1));

		FakeGrid lowBlocked = FakeGrid.room5x5x3();
		lowBlocked.set(3, 1, 1, STONE);
		RoomScan lb = scan(lowBlocked);
		assertTrue(lb.enclosed(), lb.problems().toString());
		assertEquals(new Pos(3, 2, 1), lb.start());
	}

	@Test
	void markerOnTheInsideIsWrongSide() {
		RoomScan s = RoomScanner.scanFromMarker(FakeGrid.room5x5x3(), new Pos(3, 3, 1), Dir.SOUTH);
		assertProblem(s, Kind.WRONG_SIDE, new Pos(3, 3, 1));
		assertEquals("The marker is on the inside: put it on the outside wall", s.problems().get(0).message());
	}

	@Test
	void markerCellIsAlwaysPassable() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.set(FakeGrid.MARKER.x(), FakeGrid.MARKER.y(), FakeGrid.MARKER.z(), Cell.SOLID);
		g.clear(2, 2, 0);
		assertProblem(scan(g), Kind.WALL_GAP, new Pos(2, 2, 0));
	}

	/** T-L1-11b */
	@Test
	void twoBlockContentsViaPartner() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.set(0, 1, 3, Cell.solid("minecraft:red_bed", "#bed"));
		g.set(1, 1, 3, Cell.SOLID.withPartner(Dir.WEST));
		g.door(6, 0, 3); // lower half below the floor; only the upper half touches the room
		g.set(1, 1, 1, Cell.SOLID.withPartner(Dir.EAST));
		g.set(2, 1, 1, Cell.solid("minecraft:red_bed", "#bed"));
		RoomScan s = scan(g);
		assertTrue(s.valid(), s.problems().toString());
		assertEquals(List.of(new Pos(0, 1, 3), new Pos(2, 1, 1)), s.positions("#bed").stream().sorted().toList());
		assertEquals(2, s.count("#bed"));
		assertEquals(2, s.count("#door"));
		assertTrue(s.positions("#door").contains(new Pos(6, 0, 3)));
		assertEquals(2, s.count("minecraft:red_bed"));
	}

	/** T-L1-11 */
	@Test
	void scanIsDeterministic() {
		assertEquals(scan(FakeGrid.room5x5x3()), scan(FakeGrid.room5x5x3()));
		FakeGrid a = FakeGrid.room5x5x3();
		FakeGrid b = FakeGrid.room5x5x3();
		a.clear(2, 4, 4).set(1, 1, 1, Cell.solid("minecraft:chest", "#chest")).set(2, 1, 1, Cell.solid("minecraft:barrel"));
		b.clear(2, 4, 4).set(1, 1, 1, Cell.solid("minecraft:chest", "#chest")).set(2, 1, 1, Cell.solid("minecraft:barrel"));
		assertEquals(scan(a), scan(b));
		assertEquals(scan(a), scan(a));
	}

	@Test
	void resultIsImmutable() {
		RoomScan s = scan(FakeGrid.room5x5x3());
		assertThrows(UnsupportedOperationException.class, () -> s.interior().add(1L));
		assertThrows(UnsupportedOperationException.class, () -> s.shell().add(1L));
		assertThrows(UnsupportedOperationException.class, () -> s.contents().put("x", List.of()));
		assertThrows(UnsupportedOperationException.class, () -> s.problems().add(new RoomProblem(Kind.NO_DOOR, null)));
		assertTrue(s.positions("nothing").isEmpty());
	}

	@Test
	void scanFromDoorHasNoMarker() {
		RoomScan s = RoomScanner.scanFromDoor(FakeGrid.room5x5x3(), new Pos(3, 1, 0), Dir.SOUTH);
		assertNull(s.marker());
		assertTrue(s.valid());
		assertEquals(75, s.volume());
	}
}
