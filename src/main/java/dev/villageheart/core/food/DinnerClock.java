package dev.villageheart.core.food;

import dev.villageheart.core.Balance;
import dev.villageheart.core.time.DailyClock;

/** When dinner is due (M11 plan 3.2 #3): a {@link DailyClock} at {@link Balance#DINNER_TICK}. The loaded check is glue. */
public final class DinnerClock {
	private static final DailyClock CLOCK = new DailyClock(Balance.DINNER_TICK);

	private DinnerClock() {}

	public static long dayIndex(long dayTime) { return DailyClock.dayIndex(dayTime); }

	/** Due once per day index once the phase reaches 11000. {@code !=}, not {@code >}: /time set can move the day index back. */
	public static boolean due(long dayTime, long lastDinnerDay) { return CLOCK.due(dayTime, lastDinnerDay); }

	/** A claim made at dusk does not serve an instant dinner. */
	public static long initialLastDay(long dayTime) { return CLOCK.initialLastDay(dayTime); }
}
