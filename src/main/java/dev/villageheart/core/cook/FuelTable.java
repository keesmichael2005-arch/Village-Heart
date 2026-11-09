package dev.villageheart.core.cook;

import java.util.OptionalInt;

/**
 * What the Cook burns (DESIGN section 5.5: coal, charcoal, logs) and how much heat each is worth. The heat equals vanilla's furnace burn
 * times (a GameTest compares them with the game); one output item costs {@link #HEAT_PER_ITEM}, the furnace's 200-tick cook time.
 */
public final class FuelTable {
	private FuelTable() {}

	public static final int HEAT_PER_ITEM = 200;
	public static final int COAL_HEAT = 1600;
	public static final int LOG_HEAT = 300;

	/** @param burnableLog whether the item is in {@code #minecraft:logs_that_burn} (the glue knows the tag, the core does not) */
	public static OptionalInt heat(String itemId, boolean burnableLog) {
		if (itemId.equals("minecraft:coal") || itemId.equals("minecraft:charcoal")) return OptionalInt.of(COAL_HEAT);
		if (burnableLog) return OptionalInt.of(LOG_HEAT);
		return OptionalInt.empty();
	}
}
