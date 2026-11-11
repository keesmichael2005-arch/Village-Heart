package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guard gear the smith is asked for. Pieces count only where a guard draws them (worn, Barracks, Storage, or in a smith's hands on the way
 * back from repair). The Smithy chest is the player's drop-off and does not count. A broken piece counts while its repair is queued.
 * Guards have no WORN class: a committed order stays open when the old piece merely becomes repairable.
 */
public final class GuardDemand {
	private GuardDemand() {}

	/** Where a piece was seen. {@link #SMITHY} is ignored. */
	public enum Where { WORN, BARRACKS, STORAGE, CARRIED, SMITHY }

	/** One physical piece. {@code repairQueued} means a repair job already holds its material. */
	public record Piece(GearNeed need, Where where, int remaining, int max, boolean repairQueued) {}

	private static final List<GearNeed> SLOTS = List.of(GearNeed.SWORD, GearNeed.CHEST, GearNeed.LEGS, GearNeed.HEAD, GearNeed.FEET);

	public static boolean usable(Piece p) {
		if (p.where() == Where.SMITHY) return false;
		if (p.max() <= 0) return true;
		boolean broken = p.remaining() < Math.ceil(Balance.GEAR_MIN_DURABILITY * p.max() - 1e-9);
		return !broken || p.repairQueued();
	}

	/**
	 * One MISSING demand per short slot, indices {@code 0 .. guards-usable-1}. {@code destination} is Storage (guards take gear from there);
	 * the key's building is the Barracks.
	 */
	public static List<GearDemand> of(HeartLocation barracks, HeartLocation storage, int guards, List<Piece> pieces) {
		if (guards <= 0) return List.of();
		List<GearDemand> out = new ArrayList<>();
		for (GearNeed need : SLOTS) {
			int have = 0;
			for (Piece p : pieces) if (p.need() == need && usable(p)) have++;
			int missing = Math.max(0, guards - have);
			Urgency urgency = need == GearNeed.SWORD ? Urgency.U2_GUARD_WEAPON : Urgency.U3_GUARD_ARMOUR;
			for (int s = 0; s < missing; s++) {
				out.add(new GearDemand(new OrderKey(barracks, need, s), "guard", urgency, SlotState.MISSING, storage, java.util.OptionalInt.empty(),
						false));
			}
		}
		return out;
	}

	/**
	 * Presence of open guard orders for one need. A slot past the guard count is {@link SlotPresence#HIRED_DROPPED}. A slot covered only
	 * because a broken piece's repair is queued stays {@link SlotPresence#HELD} (the order is frozen). A real usable piece covers it.
	 */
	public static Map<OrderKey, SlotPresence> presence(HeartLocation barracks, GearNeed need, int guards, int realUsable, int repairQueued,
			List<OpenOrder> open) {
		int needed = Math.max(0, guards - (realUsable + repairQueued));
		int withoutRepair = Math.max(0, guards - realUsable);
		List<OpenOrder> sorted = new ArrayList<>(open);
		sorted.sort(Comparator.comparingInt(OpenOrder::progress).reversed().thenComparingLong(OpenOrder::startTick));
		Map<OrderKey, SlotPresence> out = new LinkedHashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			OpenOrder o = sorted.get(i);
			SlotPresence p;
			if (o.key().slot() >= guards || guards <= 0) p = SlotPresence.HIRED_DROPPED;
			else if (i < needed || i < withoutRepair) p = SlotPresence.HELD;
			else p = SlotPresence.COVERED;
			out.put(o.key(), p);
		}
		return out;
	}
}
