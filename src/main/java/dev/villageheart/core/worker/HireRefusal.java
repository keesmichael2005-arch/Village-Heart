package dev.villageheart.core.worker;

/** Why a core hire decision refused. Glue refusals (no village, not a villager ...) live in the Workers API. */
public enum HireRefusal { ALREADY_EMPLOYED, TIER_LOCKED, NOT_IN_PREFERRED_BUILDING, NO_FREE_SLOT }
