package dev.villageheart.core.blueprint;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/**
 * Where a builder may stand: the ring of columns one block outside the site box (docs/plans/M20.md 3.2 #6), so a roof, a closing wall or a
 * fence ring can never trap it and it never places a block into itself.
 */
public final class StandPoints {
	private StandPoints() {}

	/** The feet y of a standable spot in a column (solid below, two passable cells), if there is one. */
	@FunctionalInterface
	public interface Standable {
		OptionalInt feetY(int x, int z);
	}

	/** The columns of the box grown by 1 in x and z (a ring one block wide), in a fixed order, each with its feet y; unstandable ones are left out. */
	public static List<Pos> ring(Pos min, Pos max, Standable standable) {
		List<Pos> out = new ArrayList<>();
		for (int x = min.x() - 1; x <= max.x() + 1; x++) {
			for (int z = min.z() - 1; z <= max.z() + 1; z++) {
				boolean edge = x == min.x() - 1 || x == max.x() + 1 || z == min.z() - 1 || z == max.z() + 1;
				if (!edge) continue;
				OptionalInt y = standable.feetY(x, z);
				if (y.isPresent()) out.add(new Pos(x, y.getAsInt(), z));
			}
		}
		return out;
	}

	/** The corner columns of the ring (those that are also stand points are the waypoints of {@link #waypoint}). */
	public static List<int[]> corners(Pos min, Pos max) {
		return List.of(new int[] {min.x() - 1, min.z() - 1}, new int[] {max.x() + 1, min.z() - 1},
				new int[] {max.x() + 1, max.z() + 1}, new int[] {min.x() - 1, max.z() + 1});
	}

	/** 3D distance from a standing builder (feet at the centre of the column) to the centre of a cell. */
	public static double distance(Pos stand, Pos cell) {
		double dx = stand.x() - cell.x(), dy = stand.y() - (cell.y() + 0.5), dz = stand.z() - cell.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** The stand points within {@code reach} of {@code cell}, nearest to {@code from} first (ties: position order). */
	public static List<Pos> within(Pos cell, List<Pos> points, double reach, Pos from) {
		List<Pos> out = new ArrayList<>();
		for (Pos p : points) if (distance(p, cell) <= reach) out.add(p);
		out.sort(Comparator.comparingDouble((Pos p) -> sq(p, from)).thenComparing(p -> p));
		return out;
	}

	private static double sq(Pos a, Pos b) {
		double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}

	/** The box is grown by this much for the crossing test: a walk along the ring itself (0.5 out) is not a crossing. */
	private static final double GROW = 0.4;

	/** True when the straight line (x, z) from (fx, fz) to (tx, tz) passes through the box grown by a little. */
	public static boolean crosses(Pos min, Pos max, double fx, double fz, double tx, double tz) {
		double x0 = min.x() - GROW, x1 = max.x() + 1 + GROW, z0 = min.z() - GROW, z1 = max.z() + 1 + GROW;
		double t0 = 0, t1 = 1;
		double dx = tx - fx, dz = tz - fz;
		double[] p = {-dx, dx, -dz, dz};
		double[] q = {fx - x0, x1 - fx, fz - z0, z1 - fz};
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0) {
				if (q[i] < 0) return false;
			} else {
				double r = q[i] / p[i];
				if (p[i] < 0) {
					if (r > t1) return false;
					t0 = Math.max(t0, r);
				} else {
					if (r < t0) return false;
					t1 = Math.min(t1, r);
				}
			}
		}
		return t0 < t1;
	}

	/**
	 * The ring corner to walk to first when the straight way from {@code from} to {@code target} crosses the box: of the ring's corners that
	 * are not where the builder already stands and can be reached in a straight line round the box, the one with the shortest way
	 * {@code from -> corner -> target} (a corner from which the target is in the clear is always preferred). Empty when the line is clear (or no
	 * corner qualifies). Called again at each corner, a long way round goes corner by corner.
	 */
	public static java.util.Optional<Pos> waypoint(Pos min, Pos max, List<Pos> ring, Pos from, Pos target) {
		double fx = from.x() + 0.5, fz = from.z() + 0.5, tx = target.x() + 0.5, tz = target.z() + 0.5;
		if (!crosses(min, max, fx, fz, tx, tz)) return java.util.Optional.empty();
		Pos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int[] c : corners(min, max)) {
			for (Pos r : ring) {
				if (r.x() != c[0] || r.z() != c[1]) continue;
				double cx = r.x() + 0.5, cz = r.z() + 0.5;
				if (Math.hypot(cx - fx, cz - fz) < 1.8) continue; // already there
				if (crosses(min, max, fx, fz, cx, cz)) continue;
				double score = Math.hypot(cx - fx, cz - fz) + Math.hypot(tx - cx, tz - cz) + (crosses(min, max, cx, cz, tx, tz) ? 1000 : 0);
				if (score < bestScore) {
					bestScore = score;
					best = r;
				}
			}
		}
		return java.util.Optional.ofNullable(best);
	}

	/** True when {@code p} (a position, not a block) lies inside the box grown by {@code margin}. */
	public static boolean inside(Pos min, Pos max, double x, double z, double margin) {
		return x > min.x() - margin && x < max.x() + 1 + margin && z > min.z() - margin && z < max.z() + 1 + margin;
	}
}
