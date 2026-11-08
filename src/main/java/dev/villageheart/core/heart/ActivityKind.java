package dev.villageheart.core.heart;

/** What a villager is doing now. STORAGE_FULL and STORAGE_UNREACHABLE come from M7's deposit trips, NEEDS_TOOL and NO_WORK from the job (M9). */
public enum ActivityKind {
	WORKING, IDLE, GOING_HOME, SLEEPING, RESTING, MEETING, PANICKING, HIDING, AWAY, OTHER, STORAGE_FULL, STORAGE_UNREACHABLE, NEEDS_TOOL, NO_WORK,
	/** M20: a builder waits for materials / for a block to go. */
	NEEDS_MATERIALS, BLOCKED,
	/** M11: missed two dinners or more. */
	TOO_HUNGRY
}
