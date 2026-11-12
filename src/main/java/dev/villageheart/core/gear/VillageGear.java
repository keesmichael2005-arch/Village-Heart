package dev.villageheart.core.gear;

import java.util.Map;

/**
 * What the diamond rule reads. {@code ironOrBetter} is every tool slot (one per hired tool worker) and every guard slot; the demanding key
 * is ignored by the caller. An unloaded guard and a key with an open iron-or-better order are true. Gold is not iron-or-better.
 */
public record VillageGear(Map<OrderKey, Boolean> ironOrBetter, int guards) {
	public VillageGear {
		ironOrBetter = Map.copyOf(ironOrBetter);
		if (guards < 0) throw new IllegalArgumentException("guards < 0");
	}

	public static VillageGear none() { return new VillageGear(Map.of(), 0); }
}
