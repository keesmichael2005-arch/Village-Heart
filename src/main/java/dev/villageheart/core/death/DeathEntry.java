package dev.villageheart.core.death;

import dev.villageheart.core.heart.HeartLimits;
import java.util.Objects;
import java.util.UUID;

/**
 * One villager lost. {@code seq} is the log's own counter (it keeps growing after old rows are dropped).
 * {@code day} is {@code dayTime / 24000 + 1}. Name, job and cause are cut to the Heart caps.
 */
public record DeathEntry(long seq, UUID villager, String name, String jobId, String cause, boolean byMob, long gameTime,
		int day, boolean inClaim, boolean duringRaid, boolean duringSiege) {

	public DeathEntry {
		Objects.requireNonNull(villager, "villager");
		name = HeartLimits.cut(name == null ? "" : name, HeartLimits.MAX_NAME);
		jobId = HeartLimits.cut(jobId == null ? "" : jobId, HeartLimits.MAX_JOB_ID);
		cause = HeartLimits.cut(cause == null ? "" : cause, HeartLimits.MAX_ID);
		Objects.requireNonNull(cause, "cause");
	}

	/** The same row after a conversion replaced the cause. Raid, claim and day stay as the death recorded them. */
	public DeathEntry asConversion(String convertedCause) {
		return new DeathEntry(seq, villager, name, jobId, convertedCause, DeathCauses.conversionCountsAsMob(convertedCause),
				gameTime, day, inClaim, duringRaid, duringSiege);
	}
}
