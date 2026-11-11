package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A guard's walk over the patrol points (M18 3.3): the points are in a fixed order (Heart, building door points, defence points); guard
 * {@code i} of {@code n} on a shift starts at {@code i * points / n}; {@link #next} goes to the next point that was not marked unreachable
 * within {@link Balance#PATROL_SKIP_TICKS}; when all are unreachable (or there is one) the guard stays where it is. One route per guard.
 */
public final class PatrolRoute {
	private final Map<Pos, Long> skippedAt = new HashMap<>();

	/** The first point of guard {@code index} of {@code count}. */
	public static int start(int pointCount, int index, int count) {
		if (pointCount <= 0 || count <= 0) return 0;
		return (int) ((long) index * pointCount / count) % pointCount;
	}

	public void markUnreachable(Pos point, long now) { skippedAt.put(point, now); }

	public boolean skipped(Pos point, long now) {
		Long at = skippedAt.get(point);
		return at != null && now - at < Balance.PATROL_SKIP_TICKS;
	}

	/** The point after {@code current} in the list that is not skipped, or {@code current} itself when there is none. */
	public Pos next(List<Pos> points, Pos current, long now) {
		if (points.isEmpty()) return current;
		int idx = points.indexOf(current);
		for (int j = 1; j <= points.size(); j++) {
			Pos candidate = points.get(Math.floorMod(idx + j, points.size()));
			if (!skipped(candidate, now)) return candidate;
		}
		return current;
	}
}
