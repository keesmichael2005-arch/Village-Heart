package dev.villageheart.core.heart;

/** What a villager is doing now. STORAGE_FULL and STORAGE_UNREACHABLE come from M7's deposit trips, NEEDS_TOOL and NO_WORK from the job (M9). */
public enum ActivityKind {
	WORKING, IDLE, GOING_HOME, SLEEPING, RESTING, MEETING, PANICKING, HIDING, AWAY, OTHER, STORAGE_FULL, STORAGE_UNREACHABLE, NEEDS_TOOL, NO_WORK, TOO_HUNGRY
}
