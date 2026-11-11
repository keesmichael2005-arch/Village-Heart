package dev.villageheart.core.gear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One physical stack covers one demand. Tool workers are served before guards. */
public final class OneStack {
	private OneStack() {}

	public static List<GearDemand> stillOpen(List<GearDemand> demands, List<String> storageItemIds) {
		List<GearDemand> ordered = new ArrayList<>(demands);
		ordered.sort(Comparator.comparingInt((GearDemand d) -> "guard".equals(d.job()) ? 1 : 0).thenComparingInt(d -> d.urgency().rank())
				.thenComparingInt(d -> d.key().slot()));
		Set<OrderKey> covered = new HashSet<>();
		for (String id : storageItemIds) {
			GearInfo info = GearInfo.of(id).orElse(null);
			if (info == null) continue;
			for (GearDemand d : ordered) {
				if (covered.contains(d.key()) || d.key().need() != info.need()) continue;
				covered.add(d.key());
				break;
			}
		}
		List<GearDemand> out = new ArrayList<>();
		for (GearDemand d : demands) if (!covered.contains(d.key())) out.add(d);
		return out;
	}
}
