package dev.villageheart.core;

/** The one place for tunable numbers (DESIGN section 12). */
public final class Balance {
	private Balance() {}

	/** Blocks around the Heart that count as the village (DESIGN section 1, tunable). */
	public static final int CLAIM_RADIUS = 80;

	/** How far a vanilla raid reaches. Hired workers shelter only inside this of the raid centre (M25). */
	public static final int RAID_REACH = 96;

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
	/** Vanilla 1.21.11 mining speed per tool tier ({@code ToolMaterial}), indexed by {@code ToolTier.ordinal()}: wood, stone, copper, gold, iron, diamond, netherite. */
	public static final double[] VANILLA_TOOL_SPEED = {2, 4, 5, 12, 6, 8, 9};
	/** Vanilla speed is divided by this to get the work multiplier (wood 1.0, stone 2.0, ...): the ratios between tiers are vanilla's. */
	public static final double TOOL_SPEED_DIVISOR = 2.0;
	/** Work speed multiplier per tool tier = vanilla speed / 2: wood 1.0, stone 2.0, copper 2.5, gold 6.0, iron 3.0, diamond 4.0, netherite 4.5. */
	public static final double[] TOOL_SPEED = toolSpeeds();

	private static double[] toolSpeeds() {
		double[] m = new double[VANILLA_TOOL_SPEED.length];
		for (int i = 0; i < m.length; i++) m[i] = VANILLA_TOOL_SPEED[i] / TOOL_SPEED_DIVISOR;
		return m;
	}
	/** Work speed multiplier per worker level 1..3 (M15 raises levels). */
	public static final double[] LEVEL_SPEED = {1.0, 1.25, 1.5};
	/** Speed of a job whose tool is optional and missing. */
	public static final double NO_TOOL_SPEED = 0.5;
	/** Work units (ticks at multiplier 1.0) per felled log: a stone axe (x2.0) fells a log in 40 ticks (anchored on stone; wood 80). */
	public static final double BASE_TICKS_PER_LOG = 80;
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
	/** Death-log rows kept on the village (M25). Older rows drop; {@code nextSeq} and {@code inClaimLogged} do not. */
	public static final int DEATH_LOG_MAX = 32;
	/** How often a tracked raid re-reads waves, raiders and guards. The raid's status is read every tick. */
	public static final int RAID_CHECK_TICKS = 20;
	/** How close a sheltering worker must get, and how far it may drift before it walks again. */
	public static final double SHELTER_ARRIVE = 1.5;
	public static final double SHELTER_DRIFT = 4;
	public static final int SHELTER_REPATH_TICKS = 100;
	/** A sheltering villager chooses its shelter again this often. Choosing scans every building and nearby villager. */
	public static final int SHELTER_PICK_TICKS = 20;
	/** Total XP at which a worker reaches level 2 and 3 (DESIGN section 4; {@code JobLevels.xpForNext} and {@code TierRules.levelFromXp} read them). */
	public static final int LEVEL_2_XP = 100;
	public static final int LEVEL_3_XP = 300;

	// ---- levels (M15) ----
	/** The highest job level an earned level may reach per tier (indexed by {@code Tier.ordinal()}): Hamlet 1, Village 2, Town 3. */
	public static final int[] LEVEL_CAPS = {1, 2, 3};
	// ---- tiers (M14) ----
	public static final int VILLAGE_POPULATION = 6, VILLAGE_WORKERS = 3, VILLAGE_FED_DAYS = 3;
	public static final int TOWN_POPULATION = 12, TOWN_WORKERS = 8, TOWN_GUARDS = 2, TOWN_FED_DAYS = 7, TOWN_DAWN_TREASURY = 32;
	/** The fed-days streak saturates here (it is shown as a text argument). */
	public static final int MAX_FED_STREAK = 9999;
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

	// ---- guards (M18) ----
	public static final double GUARD_BASE_DAMAGE = 1.0;
	/** Max health by level 1..3 (M18 r3 owner design: a level 1 guard has the vanilla 20, more only from levelling; tunable for M26) and bonus melee damage. */
	public static final double[] GUARD_LEVEL_HEALTH = {20, 30, 40};
	public static final double[] GUARD_LEVEL_DAMAGE = {0, 1, 2};
	public static final int DAY_DUTY_START = 0;
	public static final int NIGHT_DUTY_START = 12000;
	public static final int GUARD_DUTY_TICKS = 12000;
	public static final int GUARD_OFF_TICKS = 1000;
	public static final int GUARD_SIGHT = 24;
	/**
	 * How far past the claim edge an on-duty guard follows a raider of the tracked raid. 32 reaches the distance at which
	 * vanilla still counts a raider (about 112 from the centre); 16 left those raiders holding the wave.
	 */
	public static final int GUARD_RAID_CHASE = 32;
	/** Path length while chasing. 32 ends at a wall; 48 is the villager's own required length and reaches a gate. */
	public static final int GUARD_CHASE_PATH = 48;
	public static final int GUARD_SELF_DEFENCE_RANGE = 8;
	public static final int GUARD_SCAN_TICKS = 10;
	public static final double GUARD_CHASE_SPEED = 0.6;
	public static final int GUARD_ATTACK_COOLDOWN = 20;
	public static final int GUARD_GIVE_UP_TICKS = 200;
	public static final double GUARD_WOUNDED_BELOW = 0.3;
	public static final double GUARD_HEALED_AT = 0.8;
	/** Food ration (M18 r3): nutrition points a guard carries, the level below which it refills, ticks per bite, health per point eaten. */
	public static final int GUARD_RATION_CAP = 12;
	public static final int GUARD_RATION_REFILL_BELOW = 6;
	public static final int GUARD_EAT_TICKS = 40;
	public static final double GUARD_HEAL_PER_POINT = 1.0;
	/** A guard falls back below this fraction of its health, to a guard with at least GUARD_ALLY_HEALTHY_AT, and fights again at GUARD_HEALED_AT. */
	public static final double GUARD_RETREAT_BELOW = 0.5;
	public static final double GUARD_ALLY_HEALTHY_AT = 0.7;
	/** A guard eats only with no hostile within this many blocks. */
	public static final int GUARD_EAT_SAFE_DISTANCE = 10;
	/** Day time at which a new guard's one training day ends (dusk), and the day time from which guards are not hired (night). */
	public static final int GUARD_TRAINING_END = 12000;
	public static final int GUARD_NO_HIRE_FROM = 12000;
	/** Sparring ticks (at speed 1.0) that make one SPAR unit (1 XP). */
	public static final int SPAR_TICKS_PER_XP = 40;
	public static final int GEAR_RECHECK_TICKS = 1200;
	/** Gear with less durability left than this fraction is left for repair (shared with M19a). */
	public static final double GEAR_MIN_DURABILITY = 0.1;
	/** The guard alarm (a hostile hurt a villager in the claim) stays up this long after the last attack or hostile that hunts a villager. */
	public static final int GUARD_ALARM_HOLD_TICKS = 600;
	public static final int PATROL_DWELL_TICKS = 100;
	public static final int PATROL_SKIP_TICKS = 1200;

	// ---- gates (M18) ----
	/** A hostile mob within this many blocks of a closed gate keeps workers from opening it (an engaged guard may). */
	public static final int GATE_HOSTILE_RANGE = 8;
	/** A gate a villager opened stays open at least this long, and is closed at the latest after the longer limit (a safety net). */
	public static final int GATE_MIN_OPEN_TICKS = 5;
	public static final int GATE_MAX_OPEN_TICKS = 400;
	/** The keeper looks at the gates it opened every this many ticks. */
	public static final int GATE_KEEPER_TICKS = 5;

	// ---- builder and blueprints (M20) ----
	/**
	 * Work-clock units per action (clearing a cell, placing a block): 70 at speed 1.0 = one block per 3.5 seconds at level 1, 2.8 s at level 2,
	 * 2.3 s at level 3. Owner decision 2026-11-12 (M26): slower than the first 20 (one block a second); outside docs/M26-bounds.md's 10..30 on purpose.
	 */
	public static final double BUILD_TICKS_PER_BLOCK = 70;
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
	/** Recorded defence sites per village. A site cap, not a wall-length cap (M25: 64 was too small for a ring). */
	public static final int MAX_DEFENCE_SITES = 128;
	/** Hit points of one cobblestone defence cell (M25). A crack stores damage 1..HP_STONE-1. */
	public static final int HP_STONE = 12;
	/** Hit points of one fence-gate defence cell (M25). Two vindicator hits open it. */
	public static final int HP_GATE = 8;
	/** Cracked cells plus breaches kept for one village. Past this, the least-damaged crack is dropped. */
	public static final int MAX_DAMAGED_CELLS = 4096;
	/** Creeper breach: the target cell and the one above or below it. The splash uses {@link #CREEPER_RADIUS}. */
	public static final int CREEPER_CORE_DAMAGE = 16;
	/** Splash radius of a breaching creeper, in blocks. A powered creeper uses the wider radius; the hole stays two cells. */
	public static final int CREEPER_RADIUS = 3;
	public static final int CREEPER_RADIUS_POWERED = 6;
	public static final int CRACK_RESEND_TICKS = 200;
	public static final int DAMAGE_FLUSH_TICKS = 200;
	/** Seen wall and gate cells before zombies are lured. One wall piece is not an enclosure. */
	public static final int LURE_MIN_WALL_CELLS = 150;
	public static final int MAX_LURED = 24;
	public static final int LURE_RADIUS = 112;
	public static final int LURE_REPATH_TICKS = 60;
	public static final int LURE_NIGHT_START = 13000;
	public static final int LURE_NIGHT_END = 23000;
	public static final int MAX_SIEGE_PATHS_PER_TICK = 2;
	public static final int CREEPER_SEEK_RADIUS = 24;
	public static final int SIEGE_GRACE_DAYS = 5;
	public static final int SIEGE_GAP_DAYS = 3;
	public static final int THREAT_REST_DAYS = 2;
	public static final int RAID_GRACE_DAYS = 7;
	public static final int RAID_GAP_DAYS = 5;
	public static final int RAID_LOADED_TICKS = 600;
	public static final int THREAT_PLAYER_RANGE = 96;
	public static final int RAID_ROLL_TICK = 1000;
	public static final int SIEGE_ROLL_TICK = 13000;
	/** The day tick at which a rolled siege spawns its first zombie. */
	public static final int SIEGE_SPAWN_TICK = 18000;
	public static final int PENDING_START_MIN = 2000;
	public static final int PENDING_START_MAX = 11000;
	public static final int EARLY_RAID_WAVES = 2;
	public static final int RAID_FULL_GUARDS = 6; // M26-R: 4 -> 6 (and a Town no longer skips it, see RaidPlans)
	public static final int REWARD_CAP = 40;
	public static final int SIEGE_SPAWN_MIN = 32;
	public static final int SIEGE_SPAWN_PAD = 12;
	public static final int BREACH_NOTICE_TICKS = 1200;
	// ---- raid and siege warnings (owner, 2026-11-11) ----
	/** Chat warnings spread over this many ticks before a pending raid or siege starts. A raid is only rolled at {@link #RAID_ROLL_TICK}, so its window can be shorter. */
	public static final int WARNING_LEAD_TICKS = 6000;
	/** A title is on screen this long (ticks): fade in, stay, fade out. */
	public static final int THREAT_TITLE_FADE_IN = 10;
	public static final int THREAT_TITLE_STAY = 70;
	public static final int THREAT_TITLE_FADE_OUT = 20;
	// ---- stuck raiders: the only removal of a raider that is allowed (owner, 2026-11-11) ----
	/** No progress toward the village for this long, with nobody near, makes a raider glow. */
	public static final int STUCK_RAIDER_TICKS = 12000;
	/** A glowing raider that is still stuck this much later flees (is removed with a smoke puff). */
	public static final int STUCK_FLEE_GRACE_TICKS = 1200;
	/** A villager, guard, golem or player this close means the raider is not hiding. */
	public static final double STUCK_HELPER_RANGE = 16.0;
	/** Getting this much closer to the Heart (blocks) counts as progress. */
	public static final double STUCK_PROGRESS_BLOCKS = 2.0;
	/** A raider that hurt or was hurt this recently is fighting. */
	public static final int STUCK_COMBAT_TICKS = 600;
	public static final int STUCK_CHECK_TICKS = 20;
	/** How often unhired villagers are told to keep hiding while a raid or siege lasts. */
	public static final int SHELTER_ENFORCE_TICKS = 10;
	/** The builder repairs a breach before it starts a new build. */
	public static final boolean REPAIR_BEFORE_BUILDS = true;
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

	// ---- food, dinner and needs (M11) ----
	/** The whole village eats at this day time (DESIGN section 6). */
	public static final int DINNER_TICK = 11000;
	/** Nutrition a villager needs at dinner to count as fed: an adult 10, a baby half. */
	public static final int ADULT_NUTRITION = 10;
	public static final int CHILD_NUTRITION = 5;
	/** Work speed after 0, 1 and 2+ missed dinners (index = min(hungryDays, 2)). */
	public static final double[] HUNGER_SPEED = {1.0, 0.5, 0.0};
	/** Work speed of a worker without a bed in a valid House. */
	public static final double UNHOUSED_SPEED = 0.75;
	/** Housing is re-read (and workers re-homed) this often. */
	public static final int NEEDS_REFRESH_TICKS = 100;
	/** Items the village never eats (harmful, teleporting, or a player's valuables). Must equal the item tag villageheart:not_village_food (asserted by a test). */
	public static final java.util.Set<String> FOOD_NEVER_EAT = java.util.Set.of(
			"minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:poisonous_potato", "minecraft:pufferfish",
			"minecraft:suspicious_stew", "minecraft:chorus_fruit", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
			"minecraft:golden_carrot");
	/** Food a Cook (M13) turns into something better: eaten only after every prepared food. */
	public static final java.util.Set<String> FOOD_RAW = java.util.Set.of(
			"minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton", "minecraft:rabbit", "minecraft:cod",
			"minecraft:salmon", "minecraft:potato");

	// ---- M16: Miner + Mineshaft ---------------------------------------------------------------------

	/** Work units one haul costs; a stone pickaxe at level 1 earns 2.0 per tick, so a haul is 2000 ticks (anchored on stone; wood 4000) (2 in-game hours) underground. */
	public static final double MINER_HAUL_UNITS = 4000;
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

	// ---- M17: Hunter + Hunting Ground ---------------------------------------------------------------

	/** Work units one hunting trip costs; at level 1 (1.0 unit per tick) a trip is 3000 ticks, 3 in-game hours away (DESIGN 5.4). */
	public static final double HUNTER_TRIP_UNITS = 3000;
	/** An away hunter is stepped every this many ticks. */
	public static final int HUNTER_STEP_TICKS = 20;

	// ---- self-made tools (M19a) ---------------------------------------------------------------------------------------------
	/** Work ticks at level 1 to make a tool yourself, per CraftTier (tunable; never indexed by ordinal). */
	public static final int SELF_WOODEN_TICKS = 2400, SELF_STONE_TICKS = 4800;
	/** A tool is worn when less than this share of its durability is left (tunable). */
	/** The cancel check of every saved self-craft runs this often. */
	public static final int SELF_CANCEL_CHECK_TICKS = 100;
	/** Progress of a self-craft is saved this often. */
	public static final int SELF_CRAFT_SAVE_TICKS = 200;
	/** Swing, sound and particles every this many ticks of progress. */
	public static final int SELF_CRAFT_HIT_TICKS = 100;
	/** No fetch starts after this day time: it could not be committed and finished today. */
	public static final int SELF_CRAFT_LAST_FETCH = WORK_END - 300;
	/** With a smith serving, a MISSING required slot with no order for this many work ticks may be self-crafted. */
	public static final int SELF_CRAFT_FALLBACK_TICKS = 3000;
	/** The walk to Storage for a fetch is given up after this many ticks. */
	public static final int SELF_FETCH_TIMEOUT = 600;
	/** A building that stays registered but invalid this many work days refunds its self-crafts. */
	public static final int SELF_CRAFT_INVALID_REFUND_DAYS = 3;
	// ---- end self-made tools ----
	/** Durability the leased weapon loses per completed trip. */
	public static final int HUNTER_WEAR_PER_TRIP = 5;
	/** Completed trips per day by level (index level - 1, clamped). */
	public static final int[] HUNTER_TRIPS_PER_DAY = {2, 3, 3};
	/** A trip starts only if it can end this many ticks before WORK_END. */
	public static final int HUNTER_DEPART_MARGIN = 300;
	/** After the ground turned out unreachable the hunter waits this long before it tries again. */
	public static final int HUNTER_RETRY_TICKS = 600;
	/** Away steps in a row with an unreadable weapon chest before the hunter comes back. */
	public static final int HUNTER_UNKNOWN_LIMIT = 60;
	/** A hunter farther than this from the ground marker walks to its fletching table first (WalkTo plans at most 48 blocks). */
	public static final int HUNTER_FAR_FROM_GROUND = 40;
	/** A Hunting Ground links only to a Hunting Lodge within this many blocks (an M5 rule change, M17). */
	public static final int HUNTING_GROUND_LINK_RANGE = 40;
	/** Arrived at the ground marker when this close; also when the pathfinder can get no nearer and the hunter is within HUNT_ENTRY_CLOSEST. */
	public static final double HUNT_ENTRY_REACH = 3.0;
	public static final double HUNT_ENTRY_CLOSEST = 6.0;
	public static final int HUNT_MEAT_MIN = 2, HUNT_MEAT_MAX = 4;
	public static final int HUNT_LEATHER_MIN = 1, HUNT_LEATHER_MAX = 2;
	public static final int HUNT_FEATHER_MIN = 1, HUNT_FEATHER_MAX = 2;
	public static final double HUNT_HIDE_CHANCE = 0.5;
	/** Extra meat for a trip with a bow or crossbow and an arrow taken from the Lodge chest. */
	public static final int HUNT_ARROW_BONUS_MEAT = 1;
	/** Meat-kind draws per trip: always all of them, so an arrow never shifts the random stream. */
	public static final int HUNT_MAX_MEAT_DRAWS = HUNT_MEAT_MAX + HUNT_ARROW_BONUS_MEAT;

	// ---- M12: treasury and wages ---------------------------------------------------------------------

	/** The wage run happens when the day index changes (day time 0, the first tick of a day). */
	public static final int WAGE_TICK = 0;
	/** Emeralds paid per day by worker level 1, 2, 3 (tunable). Read through {@link #wageForLevel}. */
	private static final int[] WAGES = {1, 1, 2};
	/** The most emeralds one Withdraw action takes out. */
	public static final int MAX_WITHDRAW = 64;
	/** The treasury never holds more than this (a release drops it as item entities, one per 64). */
	public static final int MAX_TREASURY = 100_000;

	/** The daily wage of a worker of this level; a level below 1 reads as 1 and above 3 as 3. */
	public static int wageForLevel(int level) { return WAGES[Math.max(1, Math.min(level, WAGES.length)) - 1]; }

	// ---- M13: Cook + Kitchen --------------------------------------------------------------------------

	/** Work-clock cost of one output item: 1000 ticks per in-game hour / 8 items per hour (DESIGN 5.5) at speed 1.0. */
	public static final double COOK_TICKS_PER_ITEM = 125;
	/** Most output items one batch makes. */
	public static final int COOK_BATCH = 8;
	/** The cook starts no work that ends later than this day time (it must hand the food to Storage before dinner at 11000). */
	public static final int COOK_DONE_BY = WORK_END - 500;
	/** The cook works at its smoker from this close (3D). */
	public static final double COOK_REACH = 2.0;
	/** Ticks the cook stands at the crafting table before a CRAFT batch. */
	public static final int COOK_PREP_TICKS = 20;
	/** Ticks of slack added to the walk estimate of a batch. */
	public static final int COOK_WALK_SLACK = 100;
	/** Estimated ticks per block of straight distance walked (batch planning only). */
	public static final int COOK_WALK_TICKS_PER_BLOCK = 10;
	/** A fetch times out after this plus {@link #COOK_FETCH_TICKS_PER_BLOCK} per block of its legs. */
	public static final int COOK_FETCH_TIMEOUT = 400;
	public static final int COOK_FETCH_TICKS_PER_BLOCK = 20;
	/** Most (key, Storage) stops of one fetch. */
	public static final int COOK_MAX_STOPS = 6;
	/** A crafting table the cook cannot reach within this many ticks is skipped. */
	public static final int COOK_PREP_TIMEOUT = 200;
	/** Fuel kinds in the order they are burnt: charcoal and coal before logs (building wood is protected). */
	public static final java.util.List<String> COOK_FUEL_ORDER = java.util.List.of("minecraft:charcoal", "minecraft:coal");

	// ---- M22: market ---------------------------------------------------------------------------------

	/** Work units a sale costs (600 ticks at speed 1.0, 480 at level 2, 400 at level 3). */
	public static final double MERCHANT_SALE_UNITS = 600;
	/** Emeralds a merchant (or its stall) may earn per day by level 1, 2, 3. Read through {@link #merchantDailyCap}. */
	private static final int[] MERCHANT_DAILY_CAP = {16, 20, 24};
	/** Emeralds the whole village's market earns per day, however many stalls (orchestrator decision, tunable in M26). */
	public static final int MARKET_VILLAGE_DAILY_CAP = 32;
	public static final int FOOD_RESERVE_DAYS = 3, ITEM_RESERVE = 64;
	public static final int MAX_FOOD_RESERVE_DAYS = 30, MAX_ITEM_RESERVE = 1024;
	public static final int FOOD_RESERVE_STEP = 1, ITEM_RESERVE_STEP = 16;
	/** The market's shared view of the stockpile lives this many ticks; the population count lives longer. */
	public static final int MARKET_VIEW_TTL = 20, MARKET_CENSUS_TTL = 100;
	/** WORK ticks in a row away from the stall before the merchant reports that it cannot reach it. */
	public static final int MERCHANT_STALL_PATIENCE = 600;

	public static int merchantDailyCap(int level) { return MERCHANT_DAILY_CAP[Math.max(1, Math.min(level, MERCHANT_DAILY_CAP.length)) - 1]; }

	// ---- M19: Blacksmith / Smithy (all tunable) ------------------------------------------------------

	/** Blast-furnace ticks per ingot at level 1 (vanilla blasting cooking time). */
	public static final int SMITH_SMELT_TICKS = 100;
	/** Smith tool craft ticks at level 1, by tier. Not an array: leather sits between stone and copper in the enum, so never index by ordinal. */
	public static final int SMITH_TOOL_WOODEN = 600, SMITH_TOOL_STONE = 1200, SMITH_TOOL_COPPER = 1800, SMITH_TOOL_IRON = 2400, SMITH_TOOL_DIAMOND = 4800;
	/** Smith armour craft ticks at level 1, per piece. */
	public static final int SMITH_ARMOUR_LEATHER = 600, SMITH_ARMOUR_COPPER = 1800, SMITH_ARMOUR_IRON = 2400, SMITH_ARMOUR_DIAMOND = 4800;
	public static final int SMITH_STICK_TICKS = 20;
	public static final int SMITH_REPAIR_TICKS = 100;
	public static final int SMITH_BATCH = 8;
	/** A smelt batch or a repair must be able to finish by this day time (same offset as the cook). */
	public static final int SMITH_DONE_BY = WORK_END - 500;
	/** Heat the smith leaves in the stockpile while a cook is hired (2 coal). */
	public static final int SMITH_FUEL_RESERVE = 3200;
	public static final int SMITH_REPAIR_CARRY = 4;
	/** Diamonds that must remain after a diamond craft. Set high to forbid diamond gear. */
	public static final int SMITH_DIAMOND_RESERVE = 5;
	public static final int SMITH_CANCEL_CHECK_TICKS = 100;
	public static final int SMITH_ORPHAN_TICKS = 2000;
	public static final int SMITH_DELIVERY_BACKOFF = 1200;
	public static final int SMITH_XP_TICKS = 300;
	public static final int SMITH_CRAFT_SAVE_TICKS = 200;
	/** Added to the craft time when the smith decides a tool is worn (delivery allowance). */
	public static final int SMITH_DELIVERY_LEAD = 600;
	/** Charcoal, then coal; logs follow by id, the same order the cook uses. */
	public static final java.util.List<String> FUEL_ORDER = COOK_FUEL_ORDER;

	public static final int GOLEM_INGOTS = 36;
	public static final int GOLEM_BUILD_TICKS = 8000;
	/** No new golem build starts after this day time. */
	public static final int GOLEM_LAST_START = WORK_END - 2000;
	public static final int GOLEM_HAMMER_TICKS = 400;
	public static final int GOLEM_VILLAGERS_PER = 5;
	public static final double GOLEM_REPAIR_BELOW = 0.5;
	public static final int GOLEM_REPAIR_MIN_MISSING = 10;
	public static final int GOLEM_HEAL_PER_INGOT = 25;
	public static final int GOLEM_REPAIR_TICKS = 40;
	public static final double GOLEM_REPAIR_REACH = 3.0;
	public static final double GOLEM_WAIT_RADIUS = 1.5;
	public static final int GOLEM_WALK_TIMEOUT = 1200;
	public static final int GOLEM_WAIT_TIMEOUT = 1800;
	public static final int GOLEM_REPAIR_COOLDOWN = 1200;

	// ---- M23: population growth ----
	/** The stockpile must hold at least this many food days (adults 10, babies 5 a day) before a baby is licensed. Tunable. */
	public static final double GROWTH_MIN_FOOD_DAYS = 3.0;
	/** Licensed parents closer than this (blocks) and in sight of each other are seeded. Tunable. */
	public static final double COURTSHIP_SEED_DISTANCE = 4.0;
	/** Ticks of idle time that must still be left in both parents' IDLE window when a licensed pair is seeded (a make-love run is at most 325). */
	public static final int COURTSHIP_MARGIN = 400;
	/** Ticks of window time a licensed pair gets to meet before the licence lapses (CANNOT_MEET). */
	public static final int COURTSHIP_APPROACH_TICKS = 400;
	/** A licensed pair is seeded at most this often a day. */
	public static final int COURTSHIP_MAX_SEEDS = 3;
	/** A pair that could not meet is skipped for this many days while another pair exists. */
	public static final int FAILED_PAIR_DAYS = 3;
}
