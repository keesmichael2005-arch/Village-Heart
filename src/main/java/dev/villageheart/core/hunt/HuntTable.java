package dev.villageheart.core.hunt;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The yield of one hunting trip (DESIGN section 5.4). One trip draws, in this pinned order: base meat, leather, feathers, rabbit hide,
 * then {@link Balance#HUNT_MAX_MEAT_DRAWS} meat-kind draws (always all of them). So with and without an arrow the same trip index gives
 * the same base meat, leather, feathers, hide and meat kinds, and the arrow adds exactly one meat of the next kind draw.
 */
public final class HuntTable {
	private HuntTable() {}

	public static final String LEATHER = "minecraft:leather", FEATHER = "minecraft:feather", RABBIT_HIDE = "minecraft:rabbit_hide";

	/** The seven item ids a hunter can bring, in table order. */
	public static List<String> allItems() {
		List<String> out = new ArrayList<>();
		for (MeatKind k : MeatKind.values()) out.add(k.itemId());
		out.add(LEATHER);
		out.add(FEATHER);
		out.add(RABBIT_HIDE);
		return List.copyOf(out);
	}

	/** One trip: item id to count in table order, zero counts left out. */
	public static Map<String, Integer> roll(MeatWeights w, boolean arrow, RandomGenerator rng) {
		MeatWeights weights = w.total() == 0 ? MeatWeights.DEFAULT : w;
		int base = Balance.HUNT_MEAT_MIN + rng.nextInt(Balance.HUNT_MEAT_MAX - Balance.HUNT_MEAT_MIN + 1);
		int leather = Balance.HUNT_LEATHER_MIN + rng.nextInt(Balance.HUNT_LEATHER_MAX - Balance.HUNT_LEATHER_MIN + 1);
		int feathers = Balance.HUNT_FEATHER_MIN + rng.nextInt(Balance.HUNT_FEATHER_MAX - Balance.HUNT_FEATHER_MIN + 1);
		int hide = rng.nextDouble() < Balance.HUNT_HIDE_CHANCE ? 1 : 0;
		MeatKind[] kinds = new MeatKind[Balance.HUNT_MAX_MEAT_DRAWS];
		for (int i = 0; i < kinds.length; i++) kinds[i] = weights.pick(rng.nextInt(weights.total()));
		int pieces = base + (arrow ? Balance.HUNT_ARROW_BONUS_MEAT : 0);
		int[] meat = new int[MeatKind.values().length];
		for (int k = 0; k < pieces; k++) meat[kinds[k].ordinal()]++;
		Map<String, Integer> out = new LinkedHashMap<>();
		for (MeatKind k : MeatKind.values()) if (meat[k.ordinal()] > 0) out.put(k.itemId(), meat[k.ordinal()]);
		out.put(LEATHER, leather);
		out.put(FEATHER, feathers);
		if (hide > 0) out.put(RABBIT_HIDE, hide);
		return out;
	}

	/** Mean items per trip. */
	public static Map<String, Double> expected(MeatWeights w, boolean arrow) {
		MeatWeights weights = w.total() == 0 ? MeatWeights.DEFAULT : w;
		double meat = (Balance.HUNT_MEAT_MIN + Balance.HUNT_MEAT_MAX) / 2.0 + (arrow ? Balance.HUNT_ARROW_BONUS_MEAT : 0);
		Map<String, Double> out = new LinkedHashMap<>();
		for (MeatKind k : MeatKind.values()) out.put(k.itemId(), meat * weights.weight(k) / weights.total());
		out.put(LEATHER, (Balance.HUNT_LEATHER_MIN + Balance.HUNT_LEATHER_MAX) / 2.0);
		out.put(FEATHER, (Balance.HUNT_FEATHER_MIN + Balance.HUNT_FEATHER_MAX) / 2.0);
		out.put(RABBIT_HIDE, Balance.HUNT_HIDE_CHANCE);
		return out;
	}
}
