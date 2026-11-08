package dev.villageheart.core.food;

import dev.villageheart.core.Balance;

/** When dinner is due (M11 plan 3.2 #3). The day index is floorDiv(dayTime, 24000); the loaded check is glue. */
public final class DinnerClock {
	private DinnerClock() {}

	public static long dayIndex(long dayTime) { return Math.floorDiv(dayTime, (long) Balance.DAY_TICKS); }

	/** Due once per day index once the phase reaches 11000. {@code !=}, not {@code >}: /time set can move the day index back. */
	public static boolean due(long dayTime, long lastDinnerDay) {
		return Math.floorMod(dayTime, (long) Balance.DAY_TICKS) >= Balance.DINNER_TICK && dayIndex(dayTime) != lastDinnerDay;
	}

	/** A claim made at dusk does not serve an instant dinner. */
	public static long initialLastDay(long dayTime) {
		long d = dayIndex(dayTime);
		return Math.floorMod(dayTime, (long) Balance.DAY_TICKS) >= Balance.DINNER_TICK ? d : d - 1;
	}
}
