package dev.villageheart.core.gear;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.job.ToolAllocation;
import dev.villageheart.core.job.ToolRequirement;
import dev.villageheart.core.worker.JobType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/** What a camp needs in tools, per camp and need (M19a plan 3.2 #1). Pure. */
public final class ToolDemand {
	private ToolDemand() {}

	/**
	 * {@code needed = max(0, hired - fresh)} slots; slot {@code s} is MISSING when {@code hired - (fresh + worn) > s}, else WORN. Every
	 * hired worker gets its own state from a full allocation over all tools (worn included, the same ranking the lease uses), leaving out the
	 * workers in {@code crafting} (they do not use a tool while they make one; they still count as hired).
	 */
	public static CampDemand of(HeartLocation building, JobType job, List<UUID> hiredInOrder, Set<UUID> crafting, List<CampTool> tools,
			int leadTicks) {
		Optional<ToolRequirement> req = job.tool();
		if (req.isEmpty()) return CampDemand.unknown(hiredInOrder);
		WearProfile wear = WearProfile.of(job);
		GearNeed need = GearNeed.of(req.get().kind());
		Set<CampTool> wornTools = new HashSet<>();
		int fresh = 0;
		int worn = 0;
		for (CampTool t : tools) {
			if (ToolWear.worn(t.remaining(), t.max(), wear.wearPerUnit(), wear.unitTicks(), leadTicks)) {
				worn++;
				wornTools.add(t);
			} else {
				fresh++;
			}
		}
		int hired = hiredInOrder.size();
		int needed = Math.max(0, hired - fresh);
		List<GearDemand> slots = new ArrayList<>();
		for (int s = 0; s < needed; s++) {
			boolean missing = hired - (fresh + worn) > s;
			Urgency u = req.get().mode() == ToolRequirement.Mode.BONUS ? Urgency.U5_HOE
					: missing ? Urgency.U1_MISSING_REQUIRED : Urgency.U4_WORN_REQUIRED;
			boolean pace = job == JobType.LUMBERJACK || job == JobType.MINER;
			slots.add(new GearDemand(new OrderKey(building, need, s), job.id(), u, missing ? SlotState.MISSING : SlotState.WORN, building,
					OptionalInt.empty(), pace));
		}
		List<UUID> eligible = new ArrayList<>();
		for (UUID w : hiredInOrder) if (!crafting.contains(w)) eligible.add(w);
		List<ToolAllocation.Tool<CampTool>> alloc = new ArrayList<>();
		for (CampTool t : tools) alloc.add(new ToolAllocation.Tool<>(t, t.tier(), t.order(), t.remaining()));
		Map<UUID, CampTool> assigned = ToolAllocation.assign(alloc, eligible, t -> t.ref().rank());
		Map<UUID, WorkerToolState> states = new LinkedHashMap<>();
		for (UUID w : hiredInOrder) {
			CampTool t = assigned.get(w);
			states.put(w, t == null ? WorkerToolState.MISSING : wornTools.contains(t) ? WorkerToolState.WORN : WorkerToolState.FRESH);
		}
		return new CampDemand(slots, needed, states, fresh, worn);
	}

	/** A new order for this worker: own state MISSING or WORN and the camp's open orders fewer than needed; the lowest slot index no open order uses. */
	public static Optional<OrderKey> slotFor(UUID worker, CampDemand d, Set<OrderKey> openOfCamp) {
		if (d.isUnknown() || d.slots().isEmpty()) return Optional.empty();
		WorkerToolState own = d.stateOf(worker);
		if (own != WorkerToolState.MISSING && own != WorkerToolState.WORN) return Optional.empty();
		if (openOfCamp.size() >= d.needed()) return Optional.empty();
		OrderKey first = d.slots().get(0).key();
		for (int s = 0; ; s++) {
			OrderKey k = new OrderKey(first.building(), first.need(), s);
			if (!openOfCamp.contains(k)) return Optional.of(k);
		}
	}

	/**
	 * HELD for the open orders ranked (most progress first, then earliest start) below {@code needed}, COVERED for the rest; every order is
	 * UNKNOWN when the camp cannot be read.
	 */
	public static Map<OrderKey, SlotPresence> presence(CampDemand d, List<OpenOrder> openOfCamp) {
		Map<OrderKey, SlotPresence> out = new LinkedHashMap<>();
		if (d.isUnknown()) {
			for (OpenOrder o : openOfCamp) out.put(o.key(), SlotPresence.UNKNOWN);
			return out;
		}
		List<OpenOrder> sorted = new ArrayList<>(openOfCamp);
		sorted.sort(Comparator.comparingInt(OpenOrder::progress).reversed().thenComparingLong(OpenOrder::startTick));
		for (int i = 0; i < sorted.size(); i++) out.put(sorted.get(i).key(), i < d.needed() ? SlotPresence.HELD : SlotPresence.COVERED);
		return out;
	}
}
