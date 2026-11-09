package dev.villageheart.core.sim;

import java.util.List;
import java.util.Set;

/** The metric paths the assertion language may read. */
public final class MetricNames {
	private MetricNames() {}

	public static final Set<String> KNOWN = Set.of(
			"day", "claimed", "population", "adults", "babies", "employed",
			"buildings.valid", "buildings.invalid",
			"stockpile.total", "stockpile.distinct",
			"food.nutrition", "food.days_left", "treasury",
			"food.dinners", "food.diners", "food.fed", "food.hungry", "food.eaten_nutrition", "food.eaten_items", "food.max_hungry_days",
			"housing.housed", "housing.house_beds",
			"wages.runs", "wages.bill", "wages.paid", "wages.spent", "wages.strikers",
			"deaths.total", "deaths.mobs",
			"village_ms.mean", "village_ms.p95", "village_ms.max", "village_ms.samples",
			"server.mspt", "server.tps", "server.heap_used_mb",
			"world.save_bytes", "wall_seconds");

	/**
	 * Families valid for any non-empty suffix; a missing key reads 0. stockpile.tag.&lt;tag&gt; (items by item tag, M9: minecraft:logs and
	 * minecraft:saplings), work.&lt;job&gt;.&lt;counter&gt; (per-day work counts, M9) and workers.status.&lt;status&gt; (workers in each status at the end
	 * of the day, M9).
	 */
	public static final List<String> FAMILIES = List.of("stockpile.items.", "stockpile.tag.", "deaths.cause.", "work.", "workers.status.");

	public static boolean isFamily(String path) {
		for (String f : FAMILIES) if (path.startsWith(f) && path.length() > f.length()) return true;
		return false;
	}

	public static boolean isKnown(String path) {
		return KNOWN.contains(path) || isFamily(path);
	}
}
