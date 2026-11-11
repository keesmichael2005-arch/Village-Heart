package dev.villageheart.core.raid;

import java.util.Objects;

/** A raid that has ended, kept as {@code lastRaid} (M25). {@code reward} is 0 until a random-raid victory pays one. */
public record RaidSummary(int startDay, long startedAt, long endedAt, RaidOutcome outcome, RaidOrigin origin, int wavesSpawned,
		int wavesTotal, int villagersAtStart, int villagersAtEnd, int villagerDeaths, int raidersKilled, int reward) {
	public RaidSummary {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(origin, "origin");
		if (startDay < 1) throw new IllegalArgumentException("start day must be >= 1: " + startDay);
		if (wavesSpawned < 0 || wavesTotal < 0 || villagersAtStart < 0 || villagersAtEnd < 0 || villagerDeaths < 0 || raidersKilled < 0
				|| reward < 0) {
			throw new IllegalArgumentException("raid summary counts must be >= 0");
		}
	}

	public RaidSummary withReward(int reward) {
		return new RaidSummary(startDay, startedAt, endedAt, outcome, origin, wavesSpawned, wavesTotal, villagersAtStart,
				villagersAtEnd, villagerDeaths, raidersKilled, reward);
	}
}
