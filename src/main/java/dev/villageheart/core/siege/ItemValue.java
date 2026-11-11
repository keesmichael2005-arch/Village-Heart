package dev.villageheart.core.siege;

import java.util.Map;

/** What a stockpile stack is worth in emeralds (docs/plans/M25.md 3.14). */
public final class ItemValue {
	private ItemValue() {}

	public static double of(String itemId) {
		return switch (itemId) {
			case "minecraft:emerald" -> 1;
			case "minecraft:diamond" -> 2;
			case "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:gold_ingot" -> 0.25;
			default -> 1.0 / 64;
		};
	}

	public static double wealth(int treasury, Map<String, Integer> counts) {
		double w = treasury;
		for (var e : counts.entrySet()) w += e.getValue() * of(e.getKey());
		return w;
	}
}
