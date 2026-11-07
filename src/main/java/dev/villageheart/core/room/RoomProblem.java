package dev.villageheart.core.room;

import dev.villageheart.core.Balance;
import org.jetbrains.annotations.Nullable;

/** One reason a room is not valid, with the position the player should look at. */
public record RoomProblem(Kind kind, @Nullable Pos pos) {
	public enum Kind { NO_DOOR, DOORWAY_BLOCKED, WRONG_SIDE, ROOF_MISSING, WALL_GAP, TOO_BIG, NOT_LOADED, SHARED }

	public String message() {
		return switch (kind) {
			case NO_DOOR -> "No door below or beside the marker";
			case DOORWAY_BLOCKED -> "Doorway blocked at " + pos;
			case WRONG_SIDE -> "The marker is on the inside: put it on the outside wall";
			case ROOF_MISSING -> "Roof missing at " + pos;
			case WALL_GAP -> "Wall gap at " + pos;
			case TOO_BIG -> "Room too big (max " + Balance.ROOM_MAX_AIR + " air)";
			case NOT_LOADED -> "Room reaches an unloaded area at " + pos;
			case SHARED -> "Room is shared with the marker at " + pos;
		};
	}
}
