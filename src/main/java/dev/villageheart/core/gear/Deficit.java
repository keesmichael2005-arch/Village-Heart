package dev.villageheart.core.gear;

/** How much of the cheapest material is missing to make the cheapest tool ("wood" counted in planks). */
public record Deficit(GearNeed need, String cheapestMaterial, int short_) {}
