package dev.villageheart.core.worker;

/** Why a worker is not working normally (M8's screen shows it). */
public enum WorkerStatus {
	OK,
	/** Its building is invalid or its job block is no longer a slot: it idles near its building until that is fixed. */
	WORKPLACE_INVALID
}
