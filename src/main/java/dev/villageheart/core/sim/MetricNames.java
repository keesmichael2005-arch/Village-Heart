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
			"deaths.total", "deaths.mobs",
			"village_ms.mean", "village_ms.p95", "village_ms.max", "village_ms.samples",
			"server.mspt", "server.tps", "server.heap_used_mb",
			"world.save_bytes", "wall_seconds");

	/** Families valid for any non-empty suffix; a missing key reads 0. stockpile.tag is reserved for M6. */
	public static final List<String> FAMILIES = List.of("stockpile.items.", "stockpile.tag.", "deaths.cause.");

	public static boolean isFamily(String path) {
		for (String f : FAMILIES) if (path.startsWith(f) && path.length() > f.length()) return true;
		return false;
	}

	public static boolean isKnown(String path) {
		return KNOWN.contains(path) || isFamily(path);
	}
}
