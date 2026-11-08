package dev.villageheart.core.blueprint;

/**
 * Block light of a lantern in open air (docs/plans/M21.md 3.2 #3): light falls by one per step, so the cells with light 1 or more are a
 * diamond (|dx| + |dy| + |dz| below the emission). Vanilla keeps natural monster spawns out of any cell with block light above 0.
 */
public final class LampLight {
	private LampLight() {}

	public static final int LANTERN_EMISSION = 15;

	/** Block light at an offset from the light source, in open air. */
	public static int blockLight(int emission, int dx, int dy, int dz) {
		return Math.max(0, emission - Math.abs(dx) - Math.abs(dy) - Math.abs(dz));
	}

	public static int blockLight(int dx, int dy, int dz) { return blockLight(LANTERN_EMISSION, dx, dy, dz); }

	/** The largest |dx| + |dz| on level ground at which a mob's feet cell still has light 1: emission - 1 - (lantern above the feet cell). */
	public static int radius(int emission, int lanternAboveFeet) { return emission - 1 - lanternAboveFeet; }
}
