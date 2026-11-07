package dev.villageheart.core.heart;

import java.util.Objects;

public record JobChoice(String jobId, int freeSlots, boolean tierLocked) {
	public JobChoice { Objects.requireNonNull(jobId, "jobId"); }
}
