package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.List;

/**
 * What a guard should pick up (M18 3.3): for each slot the best usable piece of {@code available} that strictly beats what is worn, else
 * nothing. The glue passes only what is available to this guard (stockpile count minus other holders' reservations, Barracks slots not
 * claimed), so two guards never plan the same chestplate.
 */
public final class GearPlanner {
	private GearPlanner() {}

	public static List<GearItem> plan(List<GearItem> worn, List<GearItem> available) {
		List<GearItem> picks = new ArrayList<>();
		for (GearSlot slot : GearSlot.values()) {
			GearItem wearing = null;
			for (GearItem w : worn) if (w.slot() == slot) wearing = w;
			if (wearing != null && !wearing.usable(Balance.GEAR_MIN_DURABILITY)) wearing = null; // about to break: as good as nothing
			List<GearItem> candidates = new ArrayList<>();
			for (GearItem a : available) if (a.slot() == slot) candidates.add(a);
			GearItem best = GearRanking.best(candidates).orElse(null);
			if (best == null) continue;
			if (wearing == null || GearRanking.compareStats(best, wearing) > 0) picks.add(best);
		}
		return picks;
	}
}
