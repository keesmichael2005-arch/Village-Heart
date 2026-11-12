package dev.villageheart.core.gear;

import dev.villageheart.core.HeartLocation;

/** One camp's need: the building and what it is short of. Orders are counted per camp, not per slot index. */
public record CampNeed(HeartLocation building, GearNeed need) {}
