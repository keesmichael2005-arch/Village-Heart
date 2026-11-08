package dev.villageheart.core.blueprint;

import dev.villageheart.core.room.Dir;

/**
 * A rotation about the anchor in 90 degree clockwise steps (seen from above). The names equal
 * {@code net.minecraft.world.level.block.Rotation}'s, so the glue maps by name; the position rule is
 * {@code StructureTemplate.transform}'s: CLOCKWISE_90 sends (dx, dz) to (-dz, dx).
 */
public enum Rotation4 {
	NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

	/** Turns the offset (dx, dz) from the anchor. Returns {x, z}. */
	public int[] apply(int dx, int dz) {
		return switch (this) {
			case NONE -> new int[] {dx, dz};
			case CLOCKWISE_90 -> new int[] {-dz, dx};
			case CLOCKWISE_180 -> new int[] {-dx, -dz};
			case COUNTERCLOCKWISE_90 -> new int[] {dz, -dx};
		};
	}

	/** The reverse turn: {@code inverse().apply(apply(dx, dz)) == (dx, dz)}. */
	public Rotation4 inverse() {
		return switch (this) {
			case NONE -> NONE;
			case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
			case CLOCKWISE_180 -> CLOCKWISE_180;
			case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
		};
	}

	/** A horizontal direction turned; UP and DOWN stay. */
	public Dir apply(Dir d) {
		if (!d.horizontal()) return d;
		Dir out = d;
		for (int i = 0; i < ordinal(); i++) out = out.clockwise();
		return out;
	}

	/** One more clockwise quarter turn. */
	public Rotation4 next() { return values()[(ordinal() + 1) % 4]; }

	/** The rotation that turns {@code from} into {@code to} (both horizontal). */
	public static Rotation4 turning(Dir from, Dir to) {
		for (Rotation4 r : values()) if (r.apply(from) == to) return r;
		throw new IllegalArgumentException(from + " -> " + to);
	}
}
