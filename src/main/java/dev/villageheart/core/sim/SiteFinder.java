package dev.villageheart.core.sim;

import java.util.Arrays;
import java.util.Optional;

/**
 * Finds the flat, untouched rectangle the {@code site} action builds on (M11 plan 3.9). Pure: the terrain is a function from a column to its
 * ground height and flags. Candidates sit on a 2-block lattice; the best has the least flatten cost, then the nearest to the Heart, then the
 * smallest (x, z): deterministic.
 */
public final class SiteFinder {
	private SiteFinder() {}

	/** @param y height of the ground block; natural: only natural ground with nothing but replaceable plants above, no water */
	public record Column(int y, boolean natural, boolean entityTicking) {}

	@FunctionalInterface
	public interface Terrain {
		/** Null for a column that is not loaded or was not sampled. */
		Column column(int x, int z);
	}

	/** @param x,z min corner; floorY the median ground height the site is flattened to */
	public record Site(int x, int z, int floorY, int cost) {}

	public static final int LATTICE = 2;

	/**
	 * @param minDist  the nearest point of the rectangle is at least this far (horizontal) from the Heart
	 * @param maxDist  the farthest corner is at most this far
	 * @param inClaim  claim radius minus the margin: all four corners must be within it (horizontal)
	 * @param maxDev   largest allowed distance of a column from the median
	 */
	public static Optional<Site> find(Terrain t, int hx, int hz, int w, int d, int minDist, int maxDist, int inClaim, int maxDev) {
		return find(t, hx, hz, w, d, minDist, maxDist, inClaim, maxDev, 0);
	}

	/** @param oddPercent percent of the columns that may be unnatural (a path, a flower bed, a post); each costs 10 more than a flat column */
	public static Optional<Site> find(Terrain t, int hx, int hz, int w, int d, int minDist, int maxDist, int inClaim, int maxDev, int oddPercent) {
		int maxOdd = w * d * oddPercent / 100;
		Site best = null;
		double bestDist = 0;
		int from = -maxDist - Math.max(w, d);
		int to = maxDist;
		int[] ys = new int[w * d];
		for (int dx = from; dx <= to; dx += LATTICE) {
			int x0 = Math.floorDiv(hx + dx, LATTICE) * LATTICE;
			for (int dz = from; dz <= to; dz += LATTICE) {
				int z0 = Math.floorDiv(hz + dz, LATTICE) * LATTICE;
				if (!distanceOk(x0, z0, w, d, hx, hz, minDist, maxDist, inClaim)) continue;
				if (!corners(t, x0, z0, w, d)) continue;
				int n = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, odd = 0;
				boolean ok = true;
				for (int x = x0; x < x0 + w && ok; x++) {
					for (int z = z0; z < z0 + d; z++) {
						Column c = t.column(x, z);
						if (c == null) { ok = false; break; }
						if (!c.natural() && ++odd > maxOdd) { ok = false; break; }
						ys[n++] = c.y();
						if (c.y() < lo) lo = c.y();
						if (c.y() > hi) hi = c.y();
						if (hi - lo > 2 * maxDev) { ok = false; break; }
					}
				}
				if (!ok) continue;
				int[] sorted = Arrays.copyOf(ys, n);
				Arrays.sort(sorted);
				int median = sorted[n / 2];
				int cost = odd * 10;
				for (int y : sorted) {
					int dev = Math.abs(y - median);
					if (dev > maxDev) { ok = false; break; }
					cost += dev;
				}
				if (!ok) continue;
				double cx = x0 + w / 2.0 - hx, cz = z0 + d / 2.0 - hz;
				double dist = Math.sqrt(cx * cx + cz * cz);
				boolean better = best == null || cost < best.cost() || (cost == best.cost() && (dist < bestDist
						|| (dist == bestDist && (x0 < best.x() || (x0 == best.x() && z0 < best.z())))));
				if (better) {
					best = new Site(x0, z0, median, cost);
					bestDist = dist;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	private static boolean corners(Terrain t, int x0, int z0, int w, int d) {
		int[][] cs = {{x0, z0}, {x0 + w - 1, z0}, {x0, z0 + d - 1}, {x0 + w - 1, z0 + d - 1}};
		for (int[] c : cs) {
			Column col = t.column(c[0], c[1]);
			if (col == null || !col.entityTicking()) return false;
		}
		return true;
	}

	private static boolean distanceOk(int x0, int z0, int w, int d, int hx, int hz, int minDist, int maxDist, int inClaim) {
		double nx = Math.max(Math.max(x0 - hx, hx - (x0 + w - 1)), 0), nz = Math.max(Math.max(z0 - hz, hz - (z0 + d - 1)), 0);
		if (Math.sqrt(nx * nx + nz * nz) < minDist) return false;
		double far = 0;
		for (int cx : new int[] {x0, x0 + w - 1}) for (int cz : new int[] {z0, z0 + d - 1}) {
			double dd = Math.sqrt((double) (cx - hx) * (cx - hx) + (double) (cz - hz) * (cz - hz));
			far = Math.max(far, dd);
		}
		return far <= maxDist && far <= inClaim;
	}
}
