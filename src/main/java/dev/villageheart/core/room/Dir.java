package dev.villageheart.core.room;

/** The six directions. Names equal net.minecraft.core.Direction's, so glue maps with {@code Dir.valueOf(direction.name())}. */
public enum Dir {
	DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

	public final int dx, dy, dz;

	Dir(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }

	public Dir opposite() {
		return switch (this) {
			case DOWN -> UP;
			case UP -> DOWN;
			case NORTH -> SOUTH;
			case SOUTH -> NORTH;
			case WEST -> EAST;
			case EAST -> WEST;
		};
	}

	public boolean horizontal() { return this != UP && this != DOWN; }

	/** Seen from above: NORTH to EAST to SOUTH to WEST. */
	public Dir clockwise() {
		return switch (this) {
			case NORTH -> EAST;
			case EAST -> SOUTH;
			case SOUTH -> WEST;
			case WEST -> NORTH;
			default -> throw new IllegalStateException("not horizontal: " + this);
		};
	}

	public Dir counterClockwise() { return clockwise().opposite(); }
}
