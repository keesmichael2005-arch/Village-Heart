package dev.villageheart.core.heart;

/** Which activities the Villagers tab shows in the warning colour: the worker cannot do its job. The switch has no default, so a new kind fails to compile until it is decided. */
public final class ActivityWarnings {
	private ActivityWarnings() {}

	public static boolean isWarning(ActivityKind a) {
		return switch (a) {
			case NEEDS_TOOL, TOOL_NO_MATERIALS, TOOL_NO_SPACE, ON_STRIKE, TOO_HUNGRY, NEEDS_FUEL -> true;
			case WORKING, IDLE, GOING_HOME, SLEEPING, RESTING, MEETING, PANICKING, HIDING, AWAY, OTHER, STORAGE_FULL, STORAGE_UNREACHABLE, NO_WORK,
					NEEDS_MATERIALS, BLOCKED, AWAY_WORKING, SITE_UNREACHABLE, MAKING_WOODEN_TOOL, MAKING_STONE_TOOL, DAILY_LIMIT, TREASURY_FULL,
					PATROLLING, FIGHTING, EQUIPPING, TRAINING, WOUNDED, SHELTERING, NO_SHELTER, REPAIRING -> false;
		};
	}
}
