package dev.villageheart.core.time;

import dev.villageheart.core.Balance;

/**
 * Something that happens once per day at {@code eventTick} (dinner at 11000, wages at 0): M12 plan 3.2 #3. The day index is
 * {@code floorDiv(dayTime, 24000)}. {@code !=}, not {@code >}: {@code /time set} can move the day index back. A jump over several days is
 * due once, never once per skipped day. Whether the village is loaded is glue.
 */
public record DailyClock(int eventTick) {
	public DailyClock {
		if (eventTick < 0 || eventTick >= Balance.DAY_TICKS) throw new IllegalArgumentException("eventTick must be in 0.." + (Balance.DAY_TICKS - 1) + ": " + eventTick);
	}

	public static long dayIndex(long dayTime) { return Math.floorDiv(dayTime, (long) Balance.DAY_TICKS); }

	private static long phase(long dayTime) { return Math.floorMod(dayTime, (long) Balance.DAY_TICKS); }

	/** Due once per day index once the phase reaches {@code eventTick}. */
	public boolean due(long dayTime, long lastDay) { return phase(dayTime) >= eventTick && dayIndex(dayTime) != lastDay; }

	/** A claim made after the event's time today does not serve an instant event. */
	public long initialLastDay(long dayTime) {
		long d = dayIndex(dayTime);
		return phase(dayTime) >= eventTick ? d : d - 1;
	}
}
