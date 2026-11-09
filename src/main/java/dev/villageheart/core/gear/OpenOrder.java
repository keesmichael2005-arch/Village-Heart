package dev.villageheart.core.gear;

/** An open order of a camp: reserved, fetching, or committed and crafting. */
public record OpenOrder(OrderKey key, int progress, long startTick) {}
