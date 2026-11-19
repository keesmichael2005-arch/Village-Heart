package dev.villageheart.core.site;

import dev.villageheart.core.Balance;

/**
 * The walk home from a flag. Straight-line distance times {@link #TICKS_PER_BLOCK}. A short walk leaves the work day as
 * it is. A long one starts no new tree and no new hunt unless the work and the walk both finish before the day ends.
 */
public final class FlagDay {
	public static final int TICKS_PER_BLOCK = 10;

	private FlagDay() {}

	/** Ticks to walk {@code blocks}, rounded up. Zero or less is no walk. */
	public static long walkTicks(double blocks) {
		if (!(blocks > 0)) return 0;
		return (long) Math.ceil(blocks * TICKS_PER_BLOCK - 1e-9);
	}

	public static double blocks(int x, int y, int z, int hx, int hy, int hz) {
		double dx = (double) x - hx, dy = (double) y - hy, dz = (double) z - hz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * The latest day time at which a new tree may start: the earlier of {@link Balance#LUMBER_LAST_FELL} and the time
	 * that still leaves the walk home. The caller compares with {@code dayTime < cutoff}.
	 */
	public static long treeCutoff(double blocksHome) {
		long byWalk = Balance.WORK_END - walkTicks(blocksHome);
		return Math.min(Balance.LUMBER_LAST_FELL, byWalk);
	}

	/** A new tree starts when the day time is still before {@link #treeCutoff}. */
	public static boolean newTree(long dayTime, double blocksHome) {
		long t = Math.floorMod(dayTime, (long) Balance.DAY_TICKS);
		return t < treeCutoff(blocksHome);
	}

	/**
	 * A new hunt starts only when the walk there, the time away, and the walk back all finish, with the same depart
	 * margin the hunter already had. A walk of zero leaves that rule as it was.
	 */
	public static boolean newHunt(long dayTime, double blocksThere, double blocksHome, double progressUnits, double multiplier) {
		if (multiplier <= 0) return false;
		long t = Math.floorMod(dayTime, (long) Balance.DAY_TICKS);
		if (t < Balance.WORK_START) return false;
		double left = Math.max(0, Balance.HUNTER_TRIP_UNITS - progressUnits);
		long away = (long) Math.ceil(left / multiplier - 1e-9);
		long need = walkTicks(blocksThere) + away + walkTicks(blocksHome) + Balance.HUNTER_DEPART_MARGIN;
		return t + need <= Balance.WORK_END;
	}

	/**
	 * An away hunter comes back with no haul when finishing the trip and walking home would pass the end of the work
	 * day. A trip that is already due is not cut short: the haul still comes home.
	 */
	public static boolean stayWouldBeLate(long dayTime, double blocksHome, double progressUnits, double multiplier) {
		if (multiplier <= 0) return false;
		long t = Math.floorMod(dayTime, (long) Balance.DAY_TICKS);
		double left = Math.max(0, Balance.HUNTER_TRIP_UNITS - progressUnits);
		if (left <= 1e-6) return false;
		long away = (long) Math.ceil(left / multiplier - 1e-9);
		return t + away + walkTicks(blocksHome) > Balance.WORK_END;
	}
}
