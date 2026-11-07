package dev.villageheart.core;

import java.util.Objects;
import java.util.UUID;

/** Placeholder; M7 extends it. */
public record WorkerRecord(UUID villager, String jobId) {
	public WorkerRecord {
		Objects.requireNonNull(villager, "villager");
		Objects.requireNonNull(jobId, "jobId");
	}
}
