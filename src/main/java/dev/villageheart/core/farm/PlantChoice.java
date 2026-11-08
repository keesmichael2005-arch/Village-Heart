package dev.villageheart.core.farm;

import dev.villageheart.core.room.Pos;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What to plant on an empty farmland cell (M10 plan 3.2 #5): the kind most common among the neighbouring crops that the farmer can get a
 * seed for; ties and no neighbours fall back to the order wheat, carrots, potatoes, beetroots.
 */
public final class PlantChoice {
	private PlantChoice() {}

	public static Optional<CropKind> pick(Map<Pos, FieldCell> neighbours, Set<CropKind> available) {
		Map<CropKind, Integer> count = new EnumMap<>(CropKind.class);
		for (FieldCell c : neighbours.values()) if (c.isCrop()) count.merge(c.crop(), 1, Integer::sum);
		CropKind best = null;
		int bestCount = -1;
		for (CropKind k : CropKind.values()) {
			if (!available.contains(k)) continue;
			int n = count.getOrDefault(k, 0);
			if (n > bestCount) {
				best = k;
				bestCount = n;
			}
		}
		return Optional.ofNullable(best);
	}
}
