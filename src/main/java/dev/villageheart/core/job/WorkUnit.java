package dev.villageheart.core.job;

/** What a job counts as one unit of work. XP per unit is {@code Balance.milliXpPerUnit} (tunable, M26). */
public enum WorkUnit {
	/** Lumberjack, 1 per log (counter work.lumberjack.logs). */
	LOG_FELLED,
	/** Farmer, 1 per mature crop block harvested. */
	CROP_HARVESTED,
	/** Cook, 1 per output item (M13). */
	ITEM_COOKED,
	/** Miner, 1 per item in a haul (M16). */
	ITEM_MINED,
	/** Hunter, per trip (M17). */
	GAME_HUNTED,
	/** Guard, per hostile killed in the claim (M18). */
	HOSTILE_KILLED,
	/** Guard, 1 per sparring work-clock unit (M18). */
	SPAR,
	/** Blacksmith, per ingot / item / repair (M19). */
	ITEM_SMITHED,
	/** Builder, 0.2 XP per block placed (500 blocks a day at one block a second) (M20). */
	BLOCK_PLACED,
	/** Merchant (M22). */
	TRADE_MADE
}
