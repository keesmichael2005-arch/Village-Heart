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
	/** The system refreshes the storage alert every this many ticks (DESIGN section 6). */
	public static final int STOCKPILE_REFRESH_TICKS = 20;
	/** A short spilling deposit keeps the "Storage full" alert up for this many ticks. */
	public static final int STORAGE_FULL_HOLD_TICKS = 200;
	/** A Storage is "unknown" while a room building within this many blocks has no cached scan yet (after a restart). */
	public static final int STOCKPILE_NEIGHBOUR_RANGE = 32;

	// ---- jobs (M9) ----
	/** Work speed multiplier per tool tier, indexed by {@code ToolTier.ordinal()}: wood, stone, copper, gold, iron, diamond, netherite. */
	public static final double[] TOOL_SPEED = {1.0, 1.25, 1.375, 1.0, 1.5, 1.75, 2.0};
	/** Work speed multiplier per worker level 1..3 (M15 raises levels). */
	public static final double[] LEVEL_SPEED = {1.0, 1.25, 1.5};
	/** Speed of a job whose tool is optional and missing. */
	public static final double NO_TOOL_SPEED = 0.5;
	/** Work units (ticks at multiplier 1.0) per felled log: a stone axe (x1.25) fells a log in 40 ticks. */
	public static final double BASE_TICKS_PER_LOG = 50;
	/** A tree counts only with this many face contacts with natural (non-persistent) leaves. */
	public static final int TREE_MIN_LEAF_CONTACTS = 3;
	public static final int MAX_TREE_LOGS = 192;
	public static final int MAX_TREE_WIDTH = 17;
	public static final int MAX_TREE_HEIGHT = 48;
	/** Trees within this many blocks (xz) of the Chopping Block, and within +-LUMBER_DY in y. */
	public static final int LUMBER_RADIUS = 32;
	public static final int LUMBER_DY = 16;
	/** The lumberjack stands within this many blocks of the tree base to chop (3D). */
	public static final double CHOP_REACH = 3.0;
	/** A tree behind its own leaves (the canopy blocks the ground up to the trunk) is chopped from the nearest spot a path leads to, within this. */
	public static final double CHOP_CLOSEST = 8.0;
	/** No new tree is started after this day time (drops would despawn before they are collected). */
	public static final int LUMBER_LAST_FELL = 5000;
	public static final int REPLANT_FALLBACK_TICKS = 6000;
	public static final int REPLANT_EXPIRY_DAYS = 3;
	/** Saplings a lumberjack keeps besides the ones the open replant spots need. */
	public static final int SAPLING_RESERVE = 4;
	/** Cell reads a tree search may spend per worker tick. */
	public static final int TREE_SEARCH_READS_PER_TICK = 2048;
	/** A swept tree list is reused for this many ticks. */
	public static final int TREE_INDEX_TTL = 1200;
	/** A tree the worker could not reach is skipped for this many ticks. */
	public static final int TREE_BLACKLIST_TICKS = 6000;
	/** Unreachable: the best distance did not improve by 1 block in this many ticks, or this many ticks in total. */
	public static final int WALK_STALL_TICKS = 200;
	public static final int WALK_TOTAL_TICKS = 800;
	/** An open Heart screen sends a fresh snapshot at most this often (and only when it changed). */
	public static final int HEART_REFRESH_TICKS = 20;
	/** Heart screen actions per player: token bucket capacity and ticks per refilled token. */
	public static final int HEART_RATE_CAPACITY = 8;
	public static final int HEART_RATE_REFILL_TICKS = 5;
	/** Total XP at which a worker reaches level 2 and 3 (DESIGN section 4; {@code JobLevels.xpForNext} and {@code TierRules.levelFromXp} read them). */
	public static final int LEVEL_2_XP = 100;
	public static final int LEVEL_3_XP = 300;

	// ---- levels (M15) ----
	/** The highest job level an earned level may reach per tier (indexed by {@code Tier.ordinal()}): Hamlet 1, Village 2, Town 3. */
	public static final int[] LEVEL_CAPS = {1, 2, 3};
	/** XP beyond level 3 has no use; XP below a capped level is banked up to here and turns into levels when the village grows. */
	public static final int MAX_BANKED_XP = LEVEL_3_XP;
	/** Thousandths of an XP per unit of work, indexed by {@code WorkUnit.ordinal()} (integers, so 0.2 never drifts). Tunable in M26. */
	private static final int[] MILLI_XP_PER_UNIT = {1000, 1000, 1000, 1000, 5000, 5000, 1000, 2000, 200, 2000};

	public static int milliXpPerUnit(dev.villageheart.core.job.WorkUnit unit) { return MILLI_XP_PER_UNIT[unit.ordinal()]; }

	public static int workUnitXpEntries() { return MILLI_XP_PER_UNIT.length; }

	/** The village's name book (M24): players it remembers by name. Ranked players are never evicted, so at most one less can be ranked. */
	public static final int MAX_KNOWN_PLAYERS = 64;
	public static final int MAX_PLAYER_NAME = 32;
	/** A building with no enclosed scan yet protects its containers within this many blocks (Chebyshev) of its marker. */
	public static final int GUARD_FALLBACK_RADIUS = 16;

	// ---- builder and blueprints (M20) ----
	/** Work-clock units per action (clearing a cell, placing a block): 20 at speed 1.0 = one block per second. */
	public static final double BUILD_TICKS_PER_BLOCK = 20;
	/** The builder acts on a cell within this distance (3D, feet to cell centre, no line of sight). */
	public static final double BUILD_REACH = 7.0;
	/** A fetch trip takes one stack's worth of materials ... */
	public static final double BUILDER_FETCH_LOAD = 1.0;
	/** ... or fills this many inventory slots. */
	public static final int BUILDER_FETCH_SLOTS = 4;
	/** The build system re-works the reservations this often. */
	public static final int BUILD_REFRESH_TICKS = 100;
	public static final int MAX_ACTIVE_BUILDS = 8;
	/** Ticks a player waits between two blueprints taken from a Builder's Table. */
	public static final int BLUEPRINT_TAKE_COOLDOWN = 10;
	public static final int BUILD_BLOCKED_RETRY_TICKS = 100;
	public static final int BUILD_IN_THE_WAY_TICKS = 100;
	public static final int BUILD_UNREACHABLE_RETRY_TICKS = 600;
	// ---- defence blueprints (M21) ----
	/** The longest wall or gate line in one blueprint, in blocks. */
	public static final int MAX_WALL_LENGTH = 32;
	/** The shortest gate: a 3-wide opening needs a post on each side. */
	public static final int GATE_MIN_LENGTH = 5;
	/** Ticks between two checks that the recorded lamp posts and watchtowers still stand. */
	public static final int DEFENCE_REFRESH_TICKS = 200;
	/** Recorded defence sites (patrol points) per village. */
	public static final int MAX_DEFENCE_SITES = 64;
	// ---- farmer (M10) ----
	/** Crops within this many blocks (xz, Euclidean) of the Farmhouse composter, and within +-FARM_DY in y. */
	public static final int FARM_RADIUS = 24;
	public static final int FARM_DY = 8;
	/** The farmer works a cell from this close (3D, to the bottom centre of the crop cell). */
	public static final double FARM_REACH = 3.0;
	/** Work units (ticks at multiplier 1.0) per action. */
	public static final double FARM_TICKS_HARVEST = 20;
	public static final double FARM_TICKS_PLANT = 10;
	public static final double FARM_TICKS_BONEMEAL = 20;
	public static final double FARM_TICKS_COMPOST = 10;
	/** Seeds (and carrots, potatoes) of each kind the farmer keeps for replanting; seeds above this are composted. */
	public static final int SEED_RESERVE = 16;
	/** The farmer walks to the composter once this many seeds are surplus. */
	public static final int COMPOST_BATCH = 8;
	/** Work speed with a hoe in the Farmhouse chest (any tier); without one it is 1.0. */
	public static final double HOE_BONUS = 1.25;
	/** Food points every worker keeps for itself (bread 4, potato/carrot/beetroot 1): vanilla's breeding threshold. */
	public static final int WORKER_FOOD_KEEP = 12;
	/** Block reads per server tick for discovering farmland, and for re-reading indexed cells. */
	public static final int FIELD_SWEEP_READS_PER_TICK = 2048;
	public static final int FIELD_REFRESH_READS_PER_TICK = 512;
	/** A complete field sweep is repeated after this many ticks (new farmland). */
	public static final int FIELD_INDEX_TTL = 1200;
	/** Farm cells beyond this many per Farmhouse are ignored (nearest first). */
	public static final int MAX_FIELD_CELLS = 2048;
	/** At most one stack of each seed is kept, so seeds can never fill the inventory. */
	public static final int SEED_KEEP_MAX = 64;
	/** With this many free inventory slots or fewer the farmer composts its surplus before it harvests more. */
	public static final int SLOT_PRESSURE_FREE_SLOTS = 2;

	// ---- M16: Miner + Mineshaft ---------------------------------------------------------------------

	/** Work units one haul costs; a stone pickaxe at level 1 earns 1.25 per tick, so a haul is 2000 ticks (2 in-game hours) underground. */
	public static final double MINER_HAUL_UNITS = 2500;
	/** An underground miner is stepped every this many ticks. */
	public static final int MINER_STEP_TICKS = 20;
	/** Durability a pickaxe loses per completed haul. */
	public static final int MINER_WEAR_PER_HAUL = 10;
	/** No new descent from this day time on (less than 600 work ticks are left). */
	public static final int MINER_LAST_DESCENT = 10400;
	/** After a shaft turned out unreachable the miner waits this long before it tries again. */
	public static final int MINER_RETRY_TICKS = 600;
	/** Underground steps in a row with an unreadable pickaxe chest (or an unknown depth with a haul due) before the miner comes up. */
	public static final int MINER_UNKNOWN_LIMIT = 60;
	/** A miner farther than this from the shaft marker walks to its stonecutter first (WalkTo plans at most 48 blocks). */
	public static final int MINER_FAR_FROM_SHAFT = 40;
	/** Arrived at the shaft entrance when this close; also when the pathfinder can get no nearer and the miner is within SHAFT_ENTRY_CLOSEST. */
	public static final double SHAFT_ENTRY_REACH = 2.5;
	public static final double SHAFT_ENTRY_CLOSEST = 5.0;
	/** Depth bands of the haul tables by the shaft's deepest Y: HIGH from 40, MID from 0, DEEP below. */
	public static final int BAND_HIGH_MIN_Y = 40;
	public static final int BAND_MID_MIN_Y = 0;
}
