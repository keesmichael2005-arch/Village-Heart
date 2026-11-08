package dev.villageheart.core.farm;

import dev.villageheart.core.room.Pos;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where the farmer uses a bone meal (M10 plan 3.2 #8): a young, unclaimed crop; non-beetroot first (a beetroot gains 0-1 age per bone
 * meal), then the lowest age fraction, then nearest to the composter, then Pos order.
 */
public final class BoneMealTargets {
	private BoneMealTargets() {}

	public static Optional<Pos> pick(List<FieldIndex.Indexed> cells, Pos composter, Set<Pos> claimed) {
		return cells.stream()
				.filter(i -> i.cell().isYoung() && !claimed.contains(i.pos()))
				.min(Comparator
						.comparingInt((FieldIndex.Indexed i) -> i.cell().crop() == CropKind.BEETROOTS ? 1 : 0)
						.thenComparingDouble(i -> (double) i.cell().age() / i.cell().crop().maxAge())
						.thenComparingLong(i -> dist2(i.pos(), composter))
						.thenComparing(FieldIndex.Indexed::pos))
				.map(FieldIndex.Indexed::pos);
	}

	static long dist2(Pos a, Pos b) {
		long dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
