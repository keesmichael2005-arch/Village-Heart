package dev.villageheart.core.raid;

/** A raid that has ended (docs/plans/M25.md 3.3). Data only. */
public record RaidSummary(int startDay, long startedAt, long endedAt, RaidOutcome outcome, RaidOrigin origin, int wavesSpawned,
		int wavesTotal, int villagersAtStart, int villagersAtEnd, int villagerDeaths, int raidersKilled, int reward) {
	public RaidSummary {
		if (outcome == null) throw new IllegalArgumentException("outcome");
		if (origin == null) throw new IllegalArgumentException("origin");
	}
}
