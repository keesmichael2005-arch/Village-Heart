package dev.villageheart.core.worker;

import dev.villageheart.core.HeartLocation;
import java.util.Objects;

/** The per-villager worker data (the entity attachment value). */
public record WorkerData(String jobId, HeartLocation building, HeartLocation jobBlock, int level, int xp, Needs needs) {
	public WorkerData {
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(jobBlock, "jobBlock");
		Objects.requireNonNull(needs, "needs");
		if (JobType.byId(jobId).isEmpty()) throw new IllegalArgumentException("unknown job: " + jobId);
		if (level < 1) throw new IllegalArgumentException("level must be >= 1: " + level);
		if (xp < 0) throw new IllegalArgumentException("xp must be >= 0: " + xp);
	}

	public static WorkerData hired(JobType job, HeartLocation building, HeartLocation jobBlock) {
		return new WorkerData(job.id(), building, jobBlock, 1, 0, Needs.DEFAULT);
	}

	public JobType job() { return JobType.byId(jobId).orElseThrow(); }
}
