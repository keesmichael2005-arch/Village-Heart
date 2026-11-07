package dev.villageheart.core.worker;

/** Why a worker is not working normally (M8's screen shows it). */
public enum WorkerStatus {
	OK,
	/** Its building is invalid or its job block is no longer a slot: it idles near its building until that is fixed. */
	WORKPLACE_INVALID,
	/** The last deposit trip found no Storage with space (or the Storage it walked to filled up); the worker keeps what it carries. */
	STORAGE_FULL,
	/** The last deposit trip timed out: the Storage door could not be reached within 600 ticks. */
	STORAGE_UNREACHABLE
}
