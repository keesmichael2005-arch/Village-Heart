package dev.villageheart.core.farm;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The Farmhouse field. The seed is farmland already in range of a composter (the disc of {@link Balance#FARM_RADIUS} by
 * {@link Balance#FARM_DY}). From those cells, farmland that shares a side is included, including a step of one block up or down.
 * A two-block rise stops it, a diagonal touch stops it, and a one-block gap stops it. The cap keeps the cells nearest to a
 * composter. Every composter of one Farmhouse is one field.
 */
public final class FieldReach {
	private FieldReach() {}

	private static final int[] DX = {1, -1, 0, 0};
	private static final int[] DZ = {0, 0, 1, -1};

	/** True when {@code cell} is inside the seed disc of {@code composter}. */
	public static boolean inSeed(Pos composter, Pos cell) { return FieldArea.contains(composter, cell); }

	/** True when {@code cell} is inside the seed disc of any composter. */
	public static boolean inSeed(List<Pos> composters, Pos cell) {
		for (Pos c : composters) if (inSeed(c, cell)) return true;
		return false;
	}

	/** Squared 3-D distance to the nearest composter. */
	public static long nearestSquared(List<Pos> composters, Pos cell) {
		long best = Long.MAX_VALUE;
		for (Pos c : composters) {
			long dx = (long) cell.x() - c.x(), dy = (long) cell.y() - c.y(), dz = (long) cell.z() - c.z();
			long d = dx * dx + dy * dy + dz * dz;
			if (d < best) best = d;
		}
		return best;
	}

	/**
	 * The crop cells of the field among {@code farmland}, at most {@link Balance#MAX_FIELD_CELLS}, nearest to a composter first.
	 * {@code farmland} is the set of crop cells that stand on farmland (the block above the farmland).
	 */
	public static List<Pos> select(List<Pos> composters, Set<Pos> farmland) {
		return select(composters, farmland, Balance.MAX_FIELD_CELLS);
	}

	/** Same, with an explicit cap (tests). */
	public static List<Pos> select(List<Pos> composters, Set<Pos> farmland, int cap) {
		if (cap <= 0 || composters.isEmpty() || farmland.isEmpty()) return List.of();
		Set<Pos> seen = new HashSet<>();
		PriorityQueue<Pos> queue = new PriorityQueue<>(Comparator
				.<Pos>comparingLong(p -> nearestSquared(composters, p))
				.thenComparing(p -> p));
		for (Pos p : farmland) {
			if (!inSeed(composters, p)) continue;
			if (seen.add(p)) queue.add(p);
		}
		List<Pos> out = new ArrayList<>();
		while (!queue.isEmpty() && out.size() < cap) {
			Pos p = queue.poll();
			out.add(p);
			for (int i = 0; i < 4; i++) {
				for (int dy = -1; dy <= 1; dy++) {
					Pos n = new Pos(p.x() + DX[i], p.y() + dy, p.z() + DZ[i]);
					if (!seen.add(n) || !farmland.contains(n)) continue;
					queue.add(n);
				}
			}
		}
		return out;
	}
}
