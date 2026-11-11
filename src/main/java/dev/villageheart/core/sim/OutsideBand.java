package dev.villageheart.core.sim;

import java.util.function.IntUnaryOperator;

/**
 * Where a {@code night_spawn} with {@code outside} stands: a band of columns just outside a wall rectangle.
 * The old placement (a disc of radius 12..20 around the Heart) landed in the vanilla village, which the ring does not
 * enclose. The band is the edge of the ring instead, including the ground in front of the south gate.
 */
public final class OutsideBand {
	/** Closest and farthest a spawned mob stands from the wall, in blocks (Chebyshev). */
	public static final int MIN = 3, MAX = 8;

	private OutsideBand() {}

	/** One column in the band. {@code roll} is uniform on {@code 0 .. bound-1} (a world's {@code RandomSource}). */
	public static long pick(IntUnaryOperator roll, int x, int z, int w, int d) {
		int band = MIN + roll.applyAsInt(MAX - MIN + 1);
		int side = roll.applyAsInt(4);
		int px = switch (side) {
			case 0 -> x - band; // west
			case 1 -> x + w - 1 + band; // east
			case 2 -> x + roll.applyAsInt(w); // north
			default -> x + roll.applyAsInt(w); // south, the gate side
		};
		int pz = switch (side) {
			case 0, 1 -> z + roll.applyAsInt(d);
			case 2 -> z - band;
			default -> z + d - 1 + band;
		};
		return pack(px, pz);
	}

	public static long pack(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	public static int x(long packed) { return (int) (packed >> 32); }

	public static int z(long packed) { return (int) packed; }

	/** 0 on or inside the rectangle; otherwise the Chebyshev distance to its nearest edge column. */
	public static int chebyshevOutside(int x0, int z0, int w, int d, int px, int pz) {
		int dx = px < x0 ? x0 - px : px > x0 + w - 1 ? px - (x0 + w - 1) : 0;
		int dz = pz < z0 ? z0 - pz : pz > z0 + d - 1 ? pz - (z0 + d - 1) : 0;
		return Math.max(dx, dz);
	}

	public static boolean inBand(int x0, int z0, int w, int d, int px, int pz) {
		int c = chebyshevOutside(x0, z0, w, d, px, pz);
		return c >= MIN && c <= MAX;
	}
}
