package dev.villageheart.core.sim;

/** The {@code wall_ring} of a scenario: the wall stands on the edge cells of the {@code w x d} rectangle whose min corner is ({@code x}, {@code z}). */
public record WallRing(int x, int z, int w, int d) {
	public static final String INSIDE = "inside", ON_WALL = "wall", OUTSIDE = "outside";

	/** Where block column ({@code bx}, {@code bz}) is: strictly inside the ring, on a wall (or gate) cell, or outside. */
	public String side(int bx, int bz) {
		int maxX = x + w - 1, maxZ = z + d - 1;
		if (bx < x || bx > maxX || bz < z || bz > maxZ) return OUTSIDE;
		if (bx == x || bx == maxX || bz == z || bz == maxZ) return ON_WALL;
		return INSIDE;
	}
}
