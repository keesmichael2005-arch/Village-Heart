package dev.villageheart.core.raid;

/**
 * The village's tracked raid (docs/plans/M25.md 3.3). Data only: track A owns {@code RaidTracker}.
 * {@code allGuardsOnDutyAt} is -1 until every qualified, non-wounded, loaded guard is on duty.
 */
public record RaidRecord(int vanillaId, RaidOrigin origin, RaidPlan plan, long startedAt, int startDay, int villagersAtStart,
		int guardsAtStart, int guardsQualifiedAtStart, int guardsWoundedAtStart, int guardsUnloadedAtStart, int wavesTotal,
		int wavesSpawned, int raidersAlive, boolean betweenWaves, int villagerDeaths, int raidersKilled, int extrasForWave,
		int extraRaidersKilled, long allGuardsOnDutyAt) {
	public RaidRecord {
		if (origin == null) throw new IllegalArgumentException("origin");
		if (plan == null) plan = RaidPlan.NONE;
	}
}
