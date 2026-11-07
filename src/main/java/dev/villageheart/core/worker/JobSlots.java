package dev.villageheart.core.worker;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure slot accounting: which job blocks may be worked, which are free, and the hire decision. */
public final class JobSlots {
	private JobSlots() {}

	/**
	 * The slot blocks of one site: blocks already held by a worker first (position order), then the rest (position order), cut to
	 * {@code site.slots()}. So adding a job block at a lower position never displaces an occupied slot.
	 */
	public static List<HeartLocation> slotBlocks(SiteView site, List<WorkerRecord> workers) {
		Set<HeartLocation> held = heldBlocks(workers);
		List<HeartLocation> sorted = new ArrayList<>(site.jobBlocks());
		sorted.sort(VillageState.locationOrder());
		List<HeartLocation> out = new ArrayList<>();
		for (HeartLocation b : sorted) if (held.contains(b)) out.add(b);
		for (HeartLocation b : sorted) if (!held.contains(b)) out.add(b);
		return List.copyOf(out.subList(0, Math.min(site.slots(), out.size())));
	}

	/** True when {@code worker}'s job block is currently a slot block of one of {@code sites} (false: building invalid, block gone). */
	public static boolean holdsSlot(List<SiteView> sites, List<WorkerRecord> workers, WorkerRecord worker) {
		for (SiteView s : sites) if (slotBlocks(s, workers).contains(worker.jobBlock())) return true;
		return false;
	}

	/** Every slot block of every site that no worker holds, in site order then position order. */
	public static List<HeartLocation> freeSlots(List<SiteView> sites, List<WorkerRecord> workers) {
		Set<HeartLocation> held = heldBlocks(workers);
		List<HeartLocation> out = new ArrayList<>();
		for (SiteView s : sites) for (HeartLocation b : slotBlocks(s, workers)) if (!held.contains(b)) out.add(b);
		return out;
	}

	/**
	 * Refusals in order, first wins: ALREADY_EMPLOYED, TIER_LOCKED, NOT_IN_PREFERRED_BUILDING (the preferred building has no free
	 * slot while another does), NO_FREE_SLOT. Otherwise takes the first free slot (in the preferred building when given).
	 */
	public static HireDecision decide(List<SiteView> sites, List<WorkerRecord> workers, Tier villageTier, UUID villager,
			JobType job, Optional<HeartLocation> preferredBuilding) {
		for (WorkerRecord w : workers) {
			if (w.villager().equals(villager)) return new HireDecision.Refused(HireRefusal.ALREADY_EMPLOYED);
		}
		if (job.tier().compareTo(villageTier) > 0) return new HireDecision.Refused(HireRefusal.TIER_LOCKED);
		Set<HeartLocation> held = heldBlocks(workers);
		boolean anyFree = false;
		for (SiteView s : sites) {
			for (HeartLocation b : slotBlocks(s, workers)) {
				if (held.contains(b)) continue;
				anyFree = true;
				if (preferredBuilding.isEmpty() || preferredBuilding.get().equals(s.building())) return new HireDecision.Hired(s, b);
			}
		}
		return new HireDecision.Refused(anyFree ? HireRefusal.NOT_IN_PREFERRED_BUILDING : HireRefusal.NO_FREE_SLOT);
	}

	private static Set<HeartLocation> heldBlocks(List<WorkerRecord> workers) {
		Set<HeartLocation> held = new HashSet<>();
		for (WorkerRecord w : workers) held.add(w.jobBlock());
		return held;
	}
}
