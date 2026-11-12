package dev.villageheart.core.heart;

/**
 * One row of the Overview's recent deaths (M25). Newest first. The siege mark waits for the wall track:
 * this row only knows whether the death happened during a raid.
 */
public record DeathRow(String name, String jobId, String cause, int day, boolean duringRaid) {
	public DeathRow {
		name = HeartLimits.cut(name == null ? "" : name, HeartLimits.MAX_NAME);
		jobId = HeartLimits.cut(jobId == null ? "" : jobId, HeartLimits.MAX_NAME);
		cause = HeartLimits.cut(cause == null ? "" : cause, HeartLimits.MAX_ID);
		if (day < 0) day = 0;
	}
}
