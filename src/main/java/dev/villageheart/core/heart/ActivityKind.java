package dev.villageheart.core.heart;

/** What a villager is doing now. STORAGE_FULL and STORAGE_UNREACHABLE come from M7's deposit trips, NEEDS_TOOL and NO_WORK from the job (M9). */
public enum ActivityKind {
	WORKING, IDLE, GOING_HOME, SLEEPING, RESTING, MEETING, PANICKING, HIDING, AWAY, OTHER, STORAGE_FULL, STORAGE_UNREACHABLE, NEEDS_TOOL, NO_WORK,
	/** M20: a builder waits for materials / for a block to go. */
	NEEDS_MATERIALS, BLOCKED,
	/** A loaded worker inside its work site (the miner in the Mineshaft, M16), and a site it cannot reach; appended so old values keep their codec index. */
	AWAY_WORKING, SITE_UNREACHABLE,
	/** M11: missed two dinners or more. */
	TOO_HUNGRY,
	/** M12: unpaid, the worker stops working until the treasury pays it. */
	ON_STRIKE,
	/** M19a: a worker making its own tool (fetching the materials or working on it), and a required tool that cannot be made. Appended. */
	MAKING_WOODEN_TOOL, MAKING_STONE_TOOL, TOOL_NO_MATERIALS, TOOL_NO_SPACE,
	/** M13: a cook with food to cook and no fuel in the stockpile. */
	NEEDS_FUEL,
	/** M18: a guard on its rounds, in a fight, fetching gear, sparring, or wounded and resting. Appended. */
	PATROLLING, FIGHTING, EQUIPPING, TRAINING, WOUNDED,
	/** M22: the merchant sold today's limit / the treasury is full. Appended. */
	DAILY_LIMIT, TREASURY_FULL
}
