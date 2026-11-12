package dev.villageheart.core.gear;

import dev.villageheart.core.smith.SmithSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Cancel rules, the next order the smith should take, and the "needs material" hints. */
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

	/**
	 * The next order in {@code classes}: urgency rank, then building, then slot. A camp is orderable while its open orders are fewer than
	 * its demand slots; the order takes that camp's lowest free slot. A demand nothing can afford is skipped and blocks nothing. An open
	 * key (a self-craft or another smith) is not ordered again.
	 */
	public static Optional<GearOrder> next(List<GearDemand> demands, Map<CampNeed, List<OpenOrder>> openByCamp, Set<Urgency> classes,
			Materials materials, int smeltableUnits, VillageGear gear, SmithSettings settings) {
		List<GearDemand> sorted = new ArrayList<>(demands);
		sorted.sort(Comparator.comparingInt((GearDemand d) -> d.urgency().rank())
				.thenComparing(d -> d.key().building().dimension())
				.thenComparingInt(d -> d.key().building().x())
				.thenComparingInt(d -> d.key().building().y())
				.thenComparingInt(d -> d.key().building().z())
				.thenComparingInt(d -> d.key().slot()));
		for (GearDemand d : sorted) {
			if (!classes.contains(d.urgency()) || d.state() == SlotState.UNKNOWN) continue;
			CampNeed camp = new CampNeed(d.key().building(), d.key().need());
			List<OpenOrder> open = openByCamp.getOrDefault(camp, List.of());
			if (open.size() >= slotsOf(demands, camp)) continue;
			boolean taken = false;
			for (OpenOrder o : open) if (o.key().equals(d.key())) taken = true;
			if (taken) continue;
			if (!isLowestFree(d, open)) continue;
			Optional<GearOrder> order = TierChoice.best(d, materials, smeltableUnits, gear, settings);
			if (order.isPresent()) return order;
		}
		return Optional.empty();
	}

	/** One deficit per need that nothing in the stockpile can make (smelting included when {@code smeltableUnits} allows it). */
	public static List<Deficit> deficits(List<GearDemand> demands, Materials materials, int smeltableUnits, VillageGear gear, SmithSettings settings) {
		Map<GearNeed, GearDemand> byNeed = new LinkedHashMap<>();
		for (GearDemand d : demands) byNeed.putIfAbsent(d.key().need(), d);
		List<Deficit> out = new ArrayList<>();
		for (GearDemand d : byNeed.values()) {
			if (TierChoice.best(d, materials, smeltableUnits, gear, settings).isPresent()) continue;
			out.add(TierChoice.cheapestShort(d, materials));
		}
		return out;
	}

	/** The plan's two-argument shape: no fuel to smelt with, and the default diamond reserve. */
	public static List<Deficit> deficits(List<GearDemand> demands, Materials materials) {
		return deficits(demands, materials, 0, VillageGear.none(), SmithSettings.defaults());
	}

	private static int slotsOf(List<GearDemand> demands, CampNeed camp) {
		int n = 0;
		for (GearDemand d : demands) if (d.key().building().equals(camp.building()) && d.key().need() == camp.need()) n++;
		return n;
	}

	/** True when no open order uses a lower slot index than this demand. */
	private static boolean isLowestFree(GearDemand d, List<OpenOrder> open) {
		for (int s = 0; s < d.key().slot(); s++) {
			OrderKey lower = new OrderKey(d.key().building(), d.key().need(), s);
			boolean used = false;
			for (OpenOrder o : open) if (o.key().equals(lower)) used = true;
			if (!used) return false;
		}
		return true;
	}
}
