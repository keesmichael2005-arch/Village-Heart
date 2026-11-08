package dev.villageheart.core.mine;

/** Why an away worker came up. Only HAUL brings something; the others are aborts. */
public enum SurfaceReason {
	HAUL, DAY_END, NO_TOOL, SITE_INVALID, NOT_A_MINER, STOPPED_WORKING
}
