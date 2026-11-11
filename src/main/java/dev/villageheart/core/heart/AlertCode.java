package dev.villageheart.core.heart;

/** Appended to by M12 (STRIKE) and M25 (RAID, SIEGE, DEFENCE_DAMAGED): keep the order of existing entries. */
public enum AlertCode { NO_STORAGE, STORAGE_FULL, INVALID_BUILDINGS, WORKPLACE_INVALID,
	/** M12: workers are on strike (danger), and the treasury cannot cover tomorrow's wages (warning). */
	STRIKE, TREASURY_SHORT,
	/** M25: a tracked raid, a running siege, and damaged walls. Appended; the snapshot lists a raid first. */
	RAID, SIEGE, DEFENCE_DAMAGED }
