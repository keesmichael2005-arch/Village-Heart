package dev.villageheart.core.worker;

/** Why a worker is not working normally (M8's screen shows it). */
public enum WorkerStatus {
	OK,
	/** Its building is invalid or its job block is no longer a slot: it idles near its building until that is fixed. */
	WORKPLACE_INVALID,
	/** The last deposit trip found no Storage with space (or the Storage it walked to filled up); the worker keeps what it carries. */
	STORAGE_FULL,
	/** The last deposit trip timed out: the Storage door could not be reached within 600 ticks. */
	STORAGE_UNREACHABLE,
	/** The job needs a tool and the camp holds none for this worker ("Needs axe"). */
	NEEDS_TOOL,
	/** The job found nothing to do ("No trees within 32 blocks"). */
	NO_WORK,
	/** A builder waits for materials the build needs (M20): the exact words are on the build row ("Needs 12 more planks"). */
	NEEDS_MATERIALS,
	/** A builder cannot go on: a block that is not natural is in the way (M20). */
	BLOCKED,
	/** The job's site (the Mineshaft) could not be reached: it retries after a while (M16). */
	SITE_UNREACHABLE,
	/** The worker is making its own tool (M19a): fetching the materials, or working on it at its job block. */
	MAKING_WOODEN_TOOL,
	MAKING_STONE_TOOL
}
