package dev.villageheart.core.sim;

import java.util.OptionalInt;

/** Which simulated day just ended. Day d (1-based) ends at game time g0 + d * 24000; {@code >=} so a skipped tick loses nothing. */
public final class SimClock {
	public static final long DAY_TICKS = 24000L;

	private final long g0;
	private final int days;
	private int nextDay;

	public SimClock(long g0, int days) {
		this(g0, days, 0);
	}

	/** A clock that has already recorded {@code completedDays} days (resume). */
	public SimClock(long g0, int days, int completedDays) {
		this.g0 = g0;
		this.days = days;
		this.nextDay = completedDays + 1;
	}

	/** The next day to record if it has ended by gameTime (and advances), else empty. Call repeatedly until empty. */
	public OptionalInt dayEnded(long gameTime) {
		if (nextDay > days || gameTime - g0 < nextDay * DAY_TICKS) return OptionalInt.empty();
		return OptionalInt.of(nextDay++);
	}

	public boolean finished() {
		return nextDay > days;
	}

	public int completedDays() {
		return nextDay - 1;
	}

	public long elapsed(long gameTime) {
		return gameTime - g0;
	}

	public long remainingTicks(long gameTime) {
		return g0 + days * DAY_TICKS - gameTime;
	}
}
