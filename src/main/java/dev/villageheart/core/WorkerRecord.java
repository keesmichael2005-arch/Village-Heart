package dev.villageheart.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The village registry entry of one worker: authoritative for who holds which job-block slot. {@code level} is a mirror of the worker's level
 * (the entity attachment holds the real one, readable only while loaded) so the wage bill can be computed for a worker that is away (M12).
 * {@code chest} is that worker's own chest, empty when an old save has not been paired yet or the room had no free chest.
 */
public record WorkerRecord(UUID villager, String jobId, HeartLocation building, HeartLocation jobBlock, int level,
		Optional<HeartLocation> chest) {
	public WorkerRecord {
		Objects.requireNonNull(villager, "villager");
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(jobBlock, "jobBlock");
		if (level < 1) throw new IllegalArgumentException("level must be >= 1: " + level);
		chest = chest == null ? Optional.empty() : chest;
	}

	/** A level 1 worker with no chest link yet. */
	public WorkerRecord(UUID villager, String jobId, HeartLocation building, HeartLocation jobBlock) {
		this(villager, jobId, building, jobBlock, 1, Optional.empty());
	}

	public WorkerRecord(UUID villager, String jobId, HeartLocation building, HeartLocation jobBlock, int level) {
		this(villager, jobId, building, jobBlock, level, Optional.empty());
	}

	public WorkerRecord withLevel(int newLevel) { return new WorkerRecord(villager, jobId, building, jobBlock, newLevel, chest); }

	public WorkerRecord withChest(@Nullable HeartLocation c) {
		return new WorkerRecord(villager, jobId, building, jobBlock, level, Optional.ofNullable(c));
	}
}
