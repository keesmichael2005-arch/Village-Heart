package dev.villageheart.core.sim;

/**
 * Which raid events belong to the day being recorded (M25 3.7). A resumed server has a new meter. Seeding it with the
 * game time and the live totals keeps a raid that started before the reload from counting as a second start, and keeps
 * the deaths from before the reload off the next day.
 */
public final class RaidDayWindow {
	private RaidDayWindow() {}

	public static boolean contains(long at, long prevRecordAt, long recordAt) {
		return at > prevRecordAt && at <= recordAt;
	}

	/** Deaths and kills new since the last sample of this same start. A different start reports its whole total. */
	public static int[] delta(long start, int deathTotal, int killTotal, long accountedStart, int accountedDeaths, int accountedKills) {
		if (start != accountedStart) return new int[] {deathTotal, killTotal};
		return new int[] {Math.max(0, deathTotal - accountedDeaths), Math.max(0, killTotal - accountedKills)};
	}
}
