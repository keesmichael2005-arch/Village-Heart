package dev.villageheart.core.blueprint;

import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * A blueprint put on the world: the ground block under its anchor and a rotation about the anchor. Layer {@code i} is at
 * {@code ground.y + base + i}.
 */
public record Placement(Pos ground, Rotation4 rotation, boolean mirrored) {
	public Placement(Pos ground, Rotation4 rotation) { this(ground, rotation, false); }

	public Placement {
		Objects.requireNonNull(ground, "ground");
		Objects.requireNonNull(rotation, "rotation");
	}

	public Pos world(Blueprint bp, int lx, int ly, int lz) {
		int dx = lx - bp.anchorX(), dz = lz - bp.anchorZ();
		if (mirrored) dx = -dx;
		int[] r = rotation.apply(dx, dz);
		return new Pos(ground.x() + r[0], ground.y() + bp.base() + ly, ground.z() + r[1]);
	}

	/** The local cell {x, layer, z} at a world position (the inverse of {@link #world}); it may lie outside the blueprint. */
	public int[] local(Blueprint bp, Pos p) {
		int[] r = rotation.inverse().apply(p.x() - ground.x(), p.z() - ground.z());
		int dx = r[0], dz = r[1];
		if (mirrored) dx = -dx;
		return new int[] {dx + bp.anchorX(), p.y() - ground.y() - bp.base(), dz + bp.anchorZ()};
	}

	/** The entry at a world position, or null outside the blueprint's box. */
	@Nullable
	public PaletteEntry entryAt(Blueprint bp, Pos p) {
		int[] l = local(bp, p);
		if (l[0] < 0 || l[0] >= bp.width() || l[1] < 0 || l[1] >= bp.height() || l[2] < 0 || l[2] >= bp.depth()) return null;
		return bp.entry(l[0], l[1], l[2]);
	}

	/** Lowest corner of the box. */
	public Pos min(Blueprint bp) {
		int[][] c = corners(bp);
		return new Pos(Math.min(Math.min(c[0][0], c[1][0]), Math.min(c[2][0], c[3][0])), ground.y() + bp.base(),
				Math.min(Math.min(c[0][1], c[1][1]), Math.min(c[2][1], c[3][1])));
	}

	/** Highest corner of the box (inclusive). */
	public Pos max(Blueprint bp) {
		int[][] c = corners(bp);
		return new Pos(Math.max(Math.max(c[0][0], c[1][0]), Math.max(c[2][0], c[3][0])), ground.y() + bp.base() + bp.height() - 1,
				Math.max(Math.max(c[0][1], c[1][1]), Math.max(c[2][1], c[3][1])));
	}

	private int[][] corners(Blueprint bp) {
		int[][] out = new int[4][];
		int i = 0;
		for (int x : new int[] {0, bp.width() - 1}) for (int z : new int[] {0, bp.depth() - 1}) {
			Pos p = world(bp, x, 0, z);
			out[i++] = new int[] {p.x(), p.z()};
		}
		return out;
	}

	/** The direction the door faces in the world. A mirror flips the local left-right before the rotation. */
	public Dir front(Blueprint bp) {
		Dir f = bp.front();
		if (mirrored && f.horizontal()) f = f == Dir.EAST ? Dir.WEST : f == Dir.WEST ? Dir.EAST : f;
		return rotation.apply(f);
	}

	public boolean inBox(Blueprint bp, Pos p) {
		Pos a = min(bp), b = max(bp);
		return p.x() >= a.x() && p.x() <= b.x() && p.y() >= a.y() && p.y() <= b.y() && p.z() >= a.z() && p.z() <= b.z();
	}
}
