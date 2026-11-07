package dev.villageheart.worker;

import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.HireRefusal;

/** The outcome of {@link Workers#hire}. */
public sealed interface HireResult {
	record Hired(WorkerRecord record) implements HireResult {}

	record Refused(HireRefusal reason) implements HireResult {}
}
