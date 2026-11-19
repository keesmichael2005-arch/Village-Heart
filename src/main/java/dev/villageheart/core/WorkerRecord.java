package dev.villageheart.core;

import java.util.Objects;
import java.util.UUID;

/**
 * The village registry entry of one worker: authoritative for who holds which job-block slot. {@code level} is a mirror of the worker's level
 * (the entity attachment holds the real one, readable only while loaded) so the wage bill can be computed for a worker that is away (M12).
 */
public record WorkerRecord(UUID villager, String jobId, HeartLocation building, HeartLocation jobBlock, int level) {
	public WorkerRecord {
		Objects.requireNonNull(villager, "villager");
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(jobBlock, "jobBlock");
		if (level < 1) throw new IllegalArgumentException("level must be >= 1: " + level);
	}

	/** A level 1 worker (a fresh hire). */
	public WorkerRecord(UUID villager, String jobId, HeartLocation building, HeartLocation jobBlock) {
		this(villager, jobId, building, jobBlock, 1);
	}

	public WorkerRecord withLevel(int newLevel) { return new WorkerRecord(villager, jobId, building, jobBlock, newLevel); }

	/** The same worker at another workplace. Job and level stay. */
	public WorkerRecord withPlace(HeartLocation newBuilding, HeartLocation newJobBlock) {
		return new WorkerRecord(villager, jobId, newBuilding, newJobBlock, level);
	}
}
