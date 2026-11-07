package dev.villageheart.core;

/** The one place for tunable numbers (DESIGN section 12). */
public final class Balance {
	private Balance() {}

	/** Blocks around the Heart that count as the village (DESIGN section 1, tunable). */
	public static final int CLAIM_RADIUS = 80;

	/** Blank Markers crafted from 8 planks around an item frame (DESIGN section 3.1, tunable). */
	public static final int BLANK_MARKER_YIELD = 1;

	/** Largest room (air cells) a building may enclose (DESIGN section 3.2, tunable). */
	public static final int ROOM_MAX_AIR = 4096;

	/** Claim radius of the two outdoor sites that sit far from the Heart (DESIGN section 2). */
	public static final int OUTDOOR_SITE_RADIUS = 128;

	/** One building is re-checked every this many ticks, round-robin (DESIGN section 3.2). */
	public static final int VALIDATION_INTERVAL_TICKS = 20;
	/** Immediate checks (placement, use, dependents) per tick. */
	public static final int MAX_PRIORITY_CHECKS_PER_TICK = 1;

	public static final int MINESHAFT_MIN_DEPTH = 12;
	public static final int MINESHAFT_MIN_RAILS = 8;
	/** A Mineshaft links to a Mine within this many blocks. */
	public static final int MINESHAFT_LINK_RANGE = 48;
	/** Horizontal (Chebyshev) reach of the shaft fill. */
	public static final int SHAFT_RADIUS = 16;
	/** Cap on the cells the shaft fill visits. */
	public static final int SHAFT_MAX_AIR = 4096;

	public static final int HUNTING_GROUND_RADIUS = 12;
	public static final int HUNTING_GROUND_MIN_NATURAL = 40;

	public static final int TRAINING_YARD_RADIUS = 6;
	public static final int TRAINING_YARD_MIN_DUMMIES = 2;

	/** Length of a Minecraft day in ticks. */
	public static final int DAY_TICKS = 24000;
	/** Worker day (DESIGN section 4, tunable): work from WORK_START to WORK_END, go home until SLEEP_START, then sleep. */
	public static final int WORK_START = 1000;
	public static final int WORK_END = 11000;
	public static final int SLEEP_START = 12000;
	/** A worker carrying this many full stacks' worth walks to Storage. */
	public static final int WORKER_CARRY_STACKS = 1;
	/** The job-block reservation pass runs this often. */
	public static final int RESERVATION_INTERVAL_TICKS = 20;
	/** A worker counts as "at" its job block within this many blocks (3D). */
	public static final double JOB_BLOCK_REACH = 1.5;
}
