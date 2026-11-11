package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;

/** When zombies walk to the village (docs/plans/M25.md 3.11). Lamps do not count. Peaceful never lures. */
public final class Lure {
	private Lure() {}

	public static boolean active(int dayTime, int seenWallOrGateCells, boolean peaceful, boolean siegeRunning) {
		if (peaceful || seenWallOrGateCells < Balance.LURE_MIN_WALL_CELLS) return false;
		if (siegeRunning) return true;
		return dayTime >= Balance.LURE_NIGHT_START && dayTime < Balance.LURE_NIGHT_END;
	}
}
