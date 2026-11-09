package dev.villageheart.core.mine;

import dev.villageheart.core.job.ToolTier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The haul tables of DESIGN section 5.3, one per {@link DepthBand}. The pickaxe tier changes the pace (elsewhere) and gates the diamond
 * only. Every chance entry draws exactly one {@code nextDouble()} in table order, whether the tier allows it or not, so the same haul
 * index gives the same results for a stone and an iron pickaxe except for the gated entry.
 */
public final class HaulTable {
	private HaulTable() {}

	private static final String COBBLE = "minecraft:cobblestone", DEEPSLATE = "minecraft:cobbled_deepslate", COAL = "minecraft:coal",
			RAW_COPPER = "minecraft:raw_copper", RAW_IRON = "minecraft:raw_iron", REDSTONE = "minecraft:redstone",
			LAPIS = "minecraft:lapis_lazuli", RAW_GOLD = "minecraft:raw_gold", DIAMOND = "minecraft:diamond";

	private static final List<HaulEntry> HIGH = List.of(
			fixed(COBBLE, 16), fixed(COAL, 2), fixed(RAW_COPPER, 1),
			chance(RAW_IRON, 1, 0.30));
	private static final List<HaulEntry> MID = List.of(
			fixed(COBBLE, 12), fixed(COAL, 2), fixed(RAW_COPPER, 2), fixed(RAW_IRON, 2),
			chance(REDSTONE, 4, 0.20), chance(LAPIS, 4, 0.10));
	private static final List<HaulEntry> DEEP = List.of(
			fixed(DEEPSLATE, 12), fixed(COAL, 1), fixed(RAW_IRON, 3),
			chance(REDSTONE, 4, 0.30), chance(LAPIS, 4, 0.20), chance(RAW_GOLD, 1, 0.20),
			new HaulEntry(DIAMOND, 1, 0.02, Optional.of(ToolTier.IRON)));

	private static HaulEntry fixed(String id, int n) { return new HaulEntry(id, n, 1.0, Optional.empty()); }

	private static HaulEntry chance(String id, int n, double p) { return new HaulEntry(id, n, p, Optional.empty()); }

	public static List<HaulEntry> entries(DepthBand b) {
		return switch (b) {
			case HIGH -> HIGH;
			case MID -> MID;
			case DEEP -> DEEP;
		};
	}

	/** One haul: item id to count, in table order, zero counts left out. */
	public static Map<String, Integer> roll(DepthBand b, ToolTier tier, RandomGenerator rng) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (HaulEntry e : entries(b)) {
			if (e.fixed()) {
				out.merge(e.itemId(), e.count(), Integer::sum);
				continue;
			}
			double d = rng.nextDouble();
			if (e.allows(tier) && d < e.chance()) out.merge(e.itemId(), e.count(), Integer::sum);
		}
		return out;
	}

	/** The mean count per haul of every item (fixed + chance x count; a gated entry counts 0). */
	public static Map<String, Double> expected(DepthBand b, ToolTier tier) {
		Map<String, Double> out = new LinkedHashMap<>();
		for (HaulEntry e : entries(b)) {
			double n = e.fixed() ? e.count() : e.allows(tier) ? e.chance() * e.count() : 0.0;
			out.merge(e.itemId(), n, Double::sum);
		}
		return out;
	}

	/** Every item id any table can give. */
	public static List<String> allItems() {
		List<String> out = new ArrayList<>();
		for (DepthBand b : DepthBand.values()) for (HaulEntry e : entries(b)) if (!out.contains(e.itemId())) out.add(e.itemId());
		return out;
	}
}
