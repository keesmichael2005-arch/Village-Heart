package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.RoomProblem.Kind;
import org.junit.jupiter.api.Test;

/** T-L1-14 */
class RoomProblemTest {
	private static final Pos P = new Pos(12, 70, -4);

	@Test
	void messagesAreExact() {
		assertEquals("No door below or beside the marker", new RoomProblem(Kind.NO_DOOR, P).message());
		assertEquals("Doorway blocked at (12, 70, -4)", new RoomProblem(Kind.DOORWAY_BLOCKED, P).message());
		assertEquals("The marker is on the inside: put it on the outside wall", new RoomProblem(Kind.WRONG_SIDE, P).message());
		assertEquals("Roof missing at (12, 70, -4)", new RoomProblem(Kind.ROOF_MISSING, P).message());
		assertEquals("Wall gap at (12, 70, -4)", new RoomProblem(Kind.WALL_GAP, P).message());
		assertEquals("Room too big (max " + Balance.ROOM_MAX_AIR + " air)", new RoomProblem(Kind.TOO_BIG, null).message());
		assertEquals("Room reaches an unloaded area at (12, 70, -4)", new RoomProblem(Kind.NOT_LOADED, P).message());
		assertEquals("Room is shared with the marker at (12, 70, -4)", new RoomProblem(Kind.SHARED, P).message());
		assertEquals(4096, Balance.ROOM_MAX_AIR);
		for (Kind k : Kind.values()) assertNotNull(new RoomProblem(k, P).message());
	}
}
