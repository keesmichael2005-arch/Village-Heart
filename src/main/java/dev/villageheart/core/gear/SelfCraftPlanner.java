package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.ToolRequirement;

/** The decision table of 3.3 (first match wins). */
public final class SelfCraftPlanner {
	private SelfCraftPlanner() {}

	public static SelfCraftDecision decide(SelfCraftFacts f) {
		boolean requiredMissing = f.own() == WorkerToolState.MISSING && f.mode() == ToolRequirement.Mode.REQUIRED;
		// 0: a fetch walk
		if (f.fetching().isPresent()) {
			if (f.fetchPresence() != SlotPresence.HELD || f.dayTime() >= Balance.WORK_END) return SelfCraftDecision.ABORT_FETCH;
			return SelfCraftDecision.KEEP_FETCHING;
		}
		// 1-4: a saved craft
		if (f.saved().isPresent()) {
			if (f.cancel().isPresent()) return new SelfCraftDecision.Cancel(f.cancel().get());
			if (f.slotState() == SlotState.UNKNOWN) return SelfCraftDecision.PAUSE;
			if (!f.atBoundary() || (f.mode() == ToolRequirement.Mode.BONUS && !f.jobIdle())) return SelfCraftDecision.NONE;
			return SelfCraftDecision.CONTINUE;
		}
		// 5-7: may it start at all
		if (f.own() == WorkerToolState.FRESH || f.own() == WorkerToolState.UNKNOWN || f.freeSlot().isEmpty()) return SelfCraftDecision.NONE;
		if (!f.atBoundary() || (f.mode() == ToolRequirement.Mode.BONUS && !f.jobIdle())) return SelfCraftDecision.NONE;
		if (!SupplyRule.selfCraftMayStart(f.smithServes(), f.slotState(), f.mode(), f.slotOpenWorkTicks())) return SelfCraftDecision.NONE;
		// 8: space first, so a full chest never makes the worker carry a tool back and forth
		if (!f.campHasSpace()) return requiredMissing ? new SelfCraftDecision.Blocked(BlockReason.NO_SPACE) : SelfCraftDecision.NONE;
		// 9: a usable tool in Storage beats crafting
		if (f.storedToolAvailable()) return SelfCraftDecision.FETCH_STORED;
		// 10-12
		if (f.plan().isEmpty()) return requiredMissing ? new SelfCraftDecision.Blocked(BlockReason.NO_MATERIALS) : SelfCraftDecision.NONE;
		if (f.dayTime() >= Balance.SELF_CRAFT_LAST_FETCH) return SelfCraftDecision.NONE;
		return new SelfCraftDecision.Start(f.plan().get());
	}
}
