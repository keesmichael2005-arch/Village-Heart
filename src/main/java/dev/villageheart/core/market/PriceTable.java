package dev.villageheart.core.market;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** What the market buys and the size of one lot (M22 plan 3.2 #3). Tunable; DESIGN section 5.9's examples are kept exactly. */
public final class PriceTable {
	private static final List<PriceEntry> ENTRIES;
	private static final Map<String, Integer> ORDER = new HashMap<>();

	static {
		List<PriceEntry> e = new ArrayList<>();
		for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"}) e.add(new PriceEntry("minecraft:" + wood + "_log", 16));
		e.add(new PriceEntry("minecraft:wheat", 20));
		e.add(new PriceEntry("minecraft:carrot", 22));
		e.add(new PriceEntry("minecraft:potato", 26));
		e.add(new PriceEntry("minecraft:beetroot", 15));
		e.add(new PriceEntry("minecraft:cobblestone", 32));
		e.add(new PriceEntry("minecraft:cobbled_deepslate", 32));
		e.add(new PriceEntry("minecraft:coal", 15));
		e.add(new PriceEntry("minecraft:raw_iron", 8));
		e.add(new PriceEntry("minecraft:raw_copper", 20));
		e.add(new PriceEntry("minecraft:iron_ingot", 4));
		e.add(new PriceEntry("minecraft:leather", 6));
		for (String meat : new String[] {"beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon"}) e.add(new PriceEntry("minecraft:cooked_" + meat, 10));
		ENTRIES = List.copyOf(e);
		for (int i = 0; i < ENTRIES.size(); i++) {
			if (ORDER.put(ENTRIES.get(i).itemId(), i) != null) throw new IllegalStateException("duplicate price entry " + ENTRIES.get(i).itemId());
		}
	}

	private PriceTable() {}

	/** In table order. */
	public static List<PriceEntry> entries() { return ENTRIES; }

	public static OptionalInt lot(String itemId) {
		Integer i = ORDER.get(itemId);
		return i == null ? OptionalInt.empty() : OptionalInt.of(ENTRIES.get(i).lot());
	}

	/** Index in the table (for ties); {@link Integer#MAX_VALUE} for an item that is not in it. */
	public static int order(String itemId) { return ORDER.getOrDefault(itemId, Integer.MAX_VALUE); }
}
