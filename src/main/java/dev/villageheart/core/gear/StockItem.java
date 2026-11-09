package dev.villageheart.core.gear;

/** One component-free stockpile item with a role; {@code available} = count minus what others reserved. */
public record StockItem(String id, MaterialRole role, long available) {}
