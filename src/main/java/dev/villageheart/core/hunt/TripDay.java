package dev.villageheart.core.hunt;

/** The trips a hunter has completed on one day. */
public record TripDay(long day, int done) {
	/** The saved count when it was saved on {@code nowDay}; any other day (earlier too: {@code /time set} can go back) counts 0. */
	public static TripDay of(long savedDay, int savedDone, long nowDay) {
		return savedDay == nowDay ? new TripDay(nowDay, Math.max(0, savedDone)) : new TripDay(nowDay, 0);
	}

	public TripDay plusOne() { return new TripDay(day, done + 1); }
}
