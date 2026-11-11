package dev.villageheart.core.raid;

import java.util.Objects;

/**
 * The raid the village is tracking (M25). Counts that happen during the raid stay here;
 * wave numbers are copied from the latest {@link RaidFacts}. {@code allGuardsOnDutyAt} is -1 until the first time
 * every loaded, qualified, unwounded guard is on duty.
 */
public record RaidRecord(int vanillaId, RaidOrigin origin, RaidPlan plan, long startedAt, int startDay, int villagersAtStart,
		int guardsAtStart, int guardsQualifiedAtStart, int guardsWoundedAtStart, int guardsUnloadedAtStart, int wavesTotal,
		int wavesSpawned, int raidersAlive, boolean betweenWaves, int villagerDeaths, int raidersKilled, int extrasForWave,
		int extraRaidersKilled, long allGuardsOnDutyAt) {
	public RaidRecord {
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(plan, "plan");
		if (vanillaId < 0) throw new IllegalArgumentException("vanilla id must be >= 0: " + vanillaId);
		if (startDay < 1) throw new IllegalArgumentException("start day must be >= 1: " + startDay);
		if (villagersAtStart < 0 || guardsAtStart < 0 || guardsQualifiedAtStart < 0 || guardsWoundedAtStart < 0
				|| guardsUnloadedAtStart < 0 || wavesTotal < 0 || wavesSpawned < 0 || raidersAlive < 0 || villagerDeaths < 0
				|| raidersKilled < 0 || extrasForWave < 0 || extraRaidersKilled < 0) {
			throw new IllegalArgumentException("raid counts must be >= 0");
		}
		if (allGuardsOnDutyAt < -1) throw new IllegalArgumentException("allGuardsOnDutyAt must be >= -1: " + allGuardsOnDutyAt);
	}

	public static RaidRecord start(RaidFacts facts, RaidTracker.Start start, long now, int day, int villagers) {
		Objects.requireNonNull(facts, "facts");
		Objects.requireNonNull(start, "start");
		return new RaidRecord(facts.vanillaId(), start.origin(), start.plan(), now, day, villagers, start.guardsAtStart(),
				start.guardsQualifiedAtStart(), start.guardsWoundedAtStart(), start.guardsUnloadedAtStart(), facts.wavesTotal(),
				facts.wavesSpawned(), facts.raidersAlive(), facts.betweenWaves(), 0, 0, 0, 0, -1L);
	}

	/** Wave and raider numbers move. Deaths, kills, the plan and the call-up time do not. */
	public RaidRecord updated(RaidFacts facts) {
		return new RaidRecord(vanillaId, origin, plan, startedAt, startDay, villagersAtStart, guardsAtStart, guardsQualifiedAtStart,
				guardsWoundedAtStart, guardsUnloadedAtStart, facts.wavesTotal(), facts.wavesSpawned(), facts.raidersAlive(),
				facts.betweenWaves(), villagerDeaths, raidersKilled, extrasForWave, extraRaidersKilled, allGuardsOnDutyAt);
	}

	public RaidRecord withVillagerDeaths(int n) {
		return new RaidRecord(vanillaId, origin, plan, startedAt, startDay, villagersAtStart, guardsAtStart, guardsQualifiedAtStart,
				guardsWoundedAtStart, guardsUnloadedAtStart, wavesTotal, wavesSpawned, raidersAlive, betweenWaves, n, raidersKilled,
				extrasForWave, extraRaidersKilled, allGuardsOnDutyAt);
	}

	public RaidRecord withKills(int killed, int extraKilled) {
		return new RaidRecord(vanillaId, origin, plan, startedAt, startDay, villagersAtStart, guardsAtStart, guardsQualifiedAtStart,
				guardsWoundedAtStart, guardsUnloadedAtStart, wavesTotal, wavesSpawned, raidersAlive, betweenWaves, villagerDeaths, killed,
				extrasForWave, extraKilled, allGuardsOnDutyAt);
	}

	public RaidRecord withExtrasForWave(int wave) {
		return new RaidRecord(vanillaId, origin, plan, startedAt, startDay, villagersAtStart, guardsAtStart, guardsQualifiedAtStart,
				guardsWoundedAtStart, guardsUnloadedAtStart, wavesTotal, wavesSpawned, raidersAlive, betweenWaves, villagerDeaths,
				raidersKilled, wave, extraRaidersKilled, allGuardsOnDutyAt);
	}

	public RaidRecord withAllGuardsOnDutyAt(long at) {
		return new RaidRecord(vanillaId, origin, plan, startedAt, startDay, villagersAtStart, guardsAtStart, guardsQualifiedAtStart,
				guardsWoundedAtStart, guardsUnloadedAtStart, wavesTotal, wavesSpawned, raidersAlive, betweenWaves, villagerDeaths,
				raidersKilled, extrasForWave, extraRaidersKilled, at);
	}

	public RaidSummary finish(RaidOutcome outcome, long endedAt, int villagersAtEnd) {
		return new RaidSummary(startDay, startedAt, endedAt, outcome, origin, wavesSpawned, wavesTotal, villagersAtStart, villagersAtEnd,
				villagerDeaths, raidersKilled, 0);
	}
}
