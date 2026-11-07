package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.RoomProblem.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-5 */
class SharedRoomsTest {
	private static final Cell STONE = Cell.solid("minecraft:stone");
	private static final Pos M1 = new Pos(3, 3, -1);   // north door
	private static final Pos M2 = new Pos(7, 3, 3);    // east door
	private static final Pos M3 = new Pos(3, 3, 7);    // south door

	private static RoomScan north(BlockView g) { return RoomScanner.scanFromMarker(g, M1, Dir.NORTH); }
	private static RoomScan east(BlockView g) { return RoomScanner.scanFromMarker(g, M2, Dir.EAST); }
	private static RoomScan south(BlockView g) { return RoomScanner.scanFromMarker(g, M3, Dir.SOUTH); }

	@Test
	void twoMarkersIntoOneRoomFailBoth() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.door(6, 1, 3);
		RoomScan s1 = north(g), s2 = east(g);
		assertTrue(s1.valid() && s2.valid());
		assertEquals(s1.roomKey(), s2.roomKey());
		Map<Pos, RoomScan> out = SharedRooms.apply(Map.of(M1, s1, M2, s2));
		assertEquals(List.of(new RoomProblem(Kind.SHARED, M2)), out.get(M1).problems());
		assertEquals(List.of(new RoomProblem(Kind.SHARED, M1)), out.get(M2).problems());
		assertFalse(out.get(M1).valid());
		assertFalse(out.get(M2).valid());
		assertTrue(out.get(M1).enclosed());
		assertEquals("Room is shared with the marker at (7, 3, 3)", out.get(M1).problems().get(0).message());
		assertTrue(s1.valid(), "inputs are not modified");
	}

	@Test
	void threeMarkersPointAtTheLowestOther() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.door(6, 1, 3).door(3, 1, 6);
		RoomScan s1 = north(g), s2 = east(g), s3 = south(g);
		Map<Pos, RoomScan> in = new LinkedHashMap<>();
		in.put(M2, s2);
		in.put(M3, s3);
		in.put(M1, s1);
		Map<Pos, RoomScan> out = SharedRooms.apply(in);
		assertEquals(List.of(M2, M3, M1), List.copyOf(out.keySet()), "iteration order is kept");
		assertEquals(new RoomProblem(Kind.SHARED, M3), out.get(M1).problems().get(0));
		assertEquals(new RoomProblem(Kind.SHARED, M1), out.get(M2).problems().get(0));
		assertEquals(new RoomProblem(Kind.SHARED, M1), out.get(M3).problems().get(0));
	}

	@Test
	void sideBySideRoomsSharingAWallAreNotShared() {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 6, 4, 6, STONE).hollow(6, 0, 0, 12, 4, 6, STONE).door(3, 1, 0).door(9, 1, 0);
		RoomScan a = RoomScanner.scanFromMarker(g, M1, Dir.NORTH);
		RoomScan b = RoomScanner.scanFromMarker(g, new Pos(9, 3, -1), Dir.NORTH);
		assertTrue(a.valid() && b.valid());
		assertNotEquals(a.roomKey(), b.roomKey());
		Map<Pos, RoomScan> out = SharedRooms.apply(Map.of(M1, a, new Pos(9, 3, -1), b));
		assertTrue(out.get(M1).valid());
		assertTrue(out.get(new Pos(9, 3, -1)).valid());
	}

	@Test
	void leakingScansAreIgnored() {
		FakeGrid g = FakeGrid.room5x5x3();
		g.door(6, 1, 3);
		RoomScan s1 = north(g);
		g.clear(4, 4, 4);
		RoomScan s2 = east(g);
		assertTrue(s1.enclosed());
		assertFalse(s2.enclosed());
		Map<Pos, RoomScan> out = SharedRooms.apply(Map.of(M1, s1, M2, s2));
		assertTrue(out.get(M1).valid());
		assertEquals(List.of(new RoomProblem(Kind.ROOF_MISSING, new Pos(4, 4, 4))), out.get(M2).problems());
	}

	@Test
	void scansWithoutAMarkerAreRejected() {
		RoomScan s = RoomScanner.scanFromDoor(FakeGrid.room5x5x3(), new Pos(3, 1, 0), Dir.SOUTH);
		assertThrows(IllegalArgumentException.class, () -> SharedRooms.apply(Map.of("door", s)));
	}
}
