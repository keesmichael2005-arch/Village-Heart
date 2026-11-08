package dev.villageheart.core.gear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** The cancel rules of open orders (M19 adds {@code next} and {@code deficits}). */
public final class GearDemandPlanner {
	private GearDemandPlanner() {}

	/**
	 * Hysteresis: HELD resets the count, UNKNOWN leaves it, a definite reason adds one; a reason is returned once the count reaches two
	 * (two definite checks in a row).
	 */
	public static CancelCheck cancel(SlotPresence presence, int consecutiveDefinite) {
		switch (presence) {
			case HELD:
				return new CancelCheck(0, Optional.empty());
			case UNKNOWN:
				return new CancelCheck(consecutiveDefinite, Optional.empty());
			default:
				int n = consecutiveDefinite + 1;
				CancelReason r = switch (presence) {
					case COVERED -> CancelReason.COVERED;
					case HIRED_DROPPED -> CancelReason.HIRED_DROPPED;
					default -> CancelReason.BUILDING_UNREGISTERED;
				};
				return new CancelCheck(n, n >= 2 ? Optional.of(r) : Optional.empty());
		}
	}

	/** The orders beyond {@code needed}, least progress first (ties: the later start first). */
	public static List<OrderKey> surplus(List<OpenOrder> openOfCamp, int needed) {
		int extra = openOfCamp.size() - Math.max(0, needed);
		if (extra <= 0) return List.of();
		List<OpenOrder> sorted = new ArrayList<>(openOfCamp);
		sorted.sort(Comparator.comparingInt(OpenOrder::progress).thenComparing(Comparator.comparingLong(OpenOrder::startTick).reversed()));
		List<OrderKey> out = new ArrayList<>();
		for (int i = 0; i < extra; i++) out.add(sorted.get(i).key());
		return out;
	}
}
