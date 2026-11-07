package dev.villageheart.core;

import java.util.Objects;
import java.util.UUID;

/** The village registry entry of one worker: authoritative for who holds which job-block slot. */
public record WorkerRecord(UUID villager, String jobId, HeartLocation building, HeartLocation jobBlock) {
	public WorkerRecord {
		Objects.requireNonNull(villager, "villager");
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(jobBlock, "jobBlock");
	}
}
