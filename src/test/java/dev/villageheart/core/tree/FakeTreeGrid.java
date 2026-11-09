package dev.villageheart.core.tree;

import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A sparse fake world for the tree scanner: soil up to y = 0 everywhere (the ground is the surface), air above, plus the cells a test
 * sets. Counts every {@code cell()} read, so cost tests are deterministic.
 */
public final class FakeTreeGrid implements TreeView {
	private final Map<Long, TreeCell> cells = new HashMap<>();
	private final Map<Long, Integer> tops = new HashMap<>();
	private final Set<Long> room = new HashSet<>();
	private final Set<Long> unloaded = new HashSet<>();
	public int reads;

	private static long col(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	public FakeTreeGrid set(int x, int y, int z, TreeCell c) {
		cells.put(Pos.pack(x, y, z), c);
		if (c.kind() != TreeCell.Kind.AIR) tops.merge(col(x, z), y, Math::max);
		return this;
	}

	public FakeTreeGrid set(Pos p, TreeCell c) { return set(p.x(), p.y(), p.z(), c); }

	public FakeTreeGrid unload(int x, int y, int z) {
		unloaded.add(Pos.pack(x, y, z));
		return this;
	}

	/** Marks a box as a valid building's room (interior or shell). */
	public FakeTreeGrid room(int x0, int y0, int z0, int x1, int y1, int z1) {
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) room.add(Pos.pack(x, y, z));
		return this;
	}

	@Override
	public TreeCell cell(int x, int y, int z) {
		reads++;
		long k = Pos.pack(x, y, z);
		if (unloaded.contains(k)) return TreeCell.UNLOADED;
		TreeCell c = cells.get(k);
		if (c != null) return c;
		return y <= 0 ? TreeCell.SOIL : TreeCell.AIR;
	}

	@Override
	public int top(int x, int z) {
		reads++;
		return tops.getOrDefault(col(x, z), 0);
	}

	@Override
	public boolean inRoom(Pos p) { return room.contains(p.pack()); }

	public void remove(int x, int y, int z) {
		cells.put(Pos.pack(x, y, z), TreeCell.AIR);
		// tops are only a search hint in tests: recompute for the column
		int top = 0;
		for (int yy = 80; yy > 0; yy--) {
			TreeCell c = cells.get(Pos.pack(x, yy, z));
			if (c != null && c.kind() != TreeCell.Kind.AIR) { top = yy; break; }
		}
		tops.put(col(x, z), top);
	}

	// ---- builders ----------------------------------------------------------------------------

	/** A canopy of leaves around the trunk top: three layers of a 5x5 (corners cut), not replacing logs. */
	private FakeTreeGrid canopy(int cx, int cz, int topY, TreeCell leaf) {
		for (int y = topY - 1; y <= topY + 1; y++) {
			int r = y == topY + 1 ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
				if (Math.abs(dx) == r && Math.abs(dz) == r && r == 2) continue;
				int x = cx + dx, z = cz + dz;
				TreeCell existing = cells.get(Pos.pack(x, y, z));
				if (existing == null || existing.kind() == TreeCell.Kind.AIR) set(x, y, z, leaf);
			}
		}
		return this;
	}

	public FakeTreeGrid oak(int bx, int bz, int height) { return tree(bx, bz, height, Species.OAK, TreeCell.naturalLeaves(Species.OAK)); }

	public FakeTreeGrid tree(int bx, int bz, int height, Species s, TreeCell leaf) {
		for (int y = 1; y <= height; y++) set(bx, y, bz, TreeCell.log(s));
		return canopy(bx, bz, height, leaf);
	}

	public FakeTreeGrid placedLeavesTree(int bx, int bz, int height) {
		return tree(bx, bz, height, Species.OAK, TreeCell.PLACED_LEAVES);
	}

	/** Trunk of {@code height} logs with a single leaf next to it only (too few contacts). */
	public FakeTreeGrid fewLeaves(int bx, int bz, int height, int leafCount) {
		for (int y = 1; y <= height; y++) set(bx, y, bz, TreeCell.log(Species.OAK));
		int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int i = 0; i < leafCount; i++) set(bx + sides[i % 4][0], height - i / 4, bz + sides[i % 4][1], TreeCell.naturalLeaves(Species.OAK));
		return this;
	}

	/** Acacia: straight up 3, then a diagonal step (edge contact only) and up 2 more. */
	public FakeTreeGrid acacia(int bx, int bz) {
		for (int y = 1; y <= 3; y++) set(bx, y, bz, TreeCell.log(Species.ACACIA));
		for (int y = 4; y <= 5; y++) set(bx + 1, y, bz + 1, TreeCell.log(Species.ACACIA));
		return canopy(bx + 1, bz + 1, 5, TreeCell.naturalLeaves(Species.ACACIA));
	}

	public FakeTreeGrid darkOak(int bx, int bz, int height) {
		for (int y = 1; y <= height; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
			set(bx + dx, y, bz + dz, TreeCell.log(Species.DARK_OAK));
		}
		canopy(bx, bz, height, TreeCell.naturalLeaves(Species.DARK_OAK));
		return canopy(bx + 1, bz + 1, height, TreeCell.naturalLeaves(Species.DARK_OAK));
	}

	public FakeTreeGrid cherry(int bx, int bz, int height) { return tree(bx, bz, height, Species.CHERRY, TreeCell.naturalLeaves(Species.CHERRY)); }

	/** An oak with a limb log that touches the trunk only at a corner (3D diagonal). */
	public FakeTreeGrid fancyOakLimb(int bx, int bz, int height) {
		oak(bx, bz, height);
		set(bx + 1, height - 1, bz + 1, TreeCell.log(Species.OAK));
		set(bx + 2, height - 1, bz + 2, TreeCell.log(Species.OAK));
		return this;
	}

	/** A wall of logs on the ground (along x). */
	public FakeTreeGrid logWall(int x0, int z, int length, int height) {
		for (int x = x0; x < x0 + length; x++) for (int y = 1; y <= height; y++) set(x, y, z, TreeCell.log(Species.OAK));
		return this;
	}

	/** A hollow log cabin (walls only) with its corner at (x0, z0). */
	public FakeTreeGrid cabin(int x0, int z0, int w, int d, int h) {
		for (int y = 1; y <= h; y++) for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
			if (x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1) set(x, y, z, TreeCell.log(Species.OAK));
		}
		return this;
	}

	/** {@code n} logs in one column higher than any tree (a floating pile is made with {@link #floating}). */
	public FakeTreeGrid floating(int bx, int by, int bz, int height) {
		for (int y = by; y < by + height; y++) set(bx, y, bz, TreeCell.log(Species.OAK));
		return canopy(bx, bz, by + height - 1, TreeCell.naturalLeaves(Species.OAK));
	}
}
