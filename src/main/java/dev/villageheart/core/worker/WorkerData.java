package dev.villageheart.core.worker;

import dev.villageheart.core.HeartLocation;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** The per-villager worker data (the entity attachment value). {@code chest} is empty on a save from before pockets. */
public record WorkerData(String jobId, HeartLocation building, HeartLocation jobBlock, int level, int xp, Needs needs,
		Optional<HeartLocation> chest) {
	public WorkerData {
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(jobBlock, "jobBlock");
		Objects.requireNonNull(needs, "needs");
		if (JobType.byId(jobId).isEmpty()) throw new IllegalArgumentException("unknown job: " + jobId);
		if (level < 1) throw new IllegalArgumentException("level must be >= 1: " + level);
		if (xp < 0) throw new IllegalArgumentException("xp must be >= 0: " + xp);
		chest = chest == null ? Optional.empty() : chest;
	}

	public WorkerData(String jobId, HeartLocation building, HeartLocation jobBlock, int level, int xp, Needs needs) {
		this(jobId, building, jobBlock, level, xp, needs, Optional.empty());
	}

	public static WorkerData hired(JobType job, HeartLocation building, HeartLocation jobBlock) {
		return new WorkerData(job.id(), building, jobBlock, 1, 0, Needs.DEFAULT, Optional.empty());
	}

	public WorkerData withNeeds(Needs n) { return new WorkerData(jobId, building, jobBlock, level, xp, n, chest); }
	/** The same worker with a new level and XP (M15). */
	public WorkerData withProgress(int newLevel, int newXp) { return new WorkerData(jobId, building, jobBlock, newLevel, newXp, needs, chest); }

	public WorkerData withChest(@Nullable HeartLocation c) {
		return new WorkerData(jobId, building, jobBlock, level, xp, needs, Optional.ofNullable(c));
	}

	public JobType job() { return JobType.byId(jobId).orElseThrow(); }
}
