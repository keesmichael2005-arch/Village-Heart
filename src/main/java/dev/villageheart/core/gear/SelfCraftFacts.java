package dev.villageheart.core.gear;

import dev.villageheart.core.job.ToolRequirement;
import java.util.Optional;

/** Everything {@link SelfCraftPlanner} looks at, gathered by the glue. */
public record SelfCraftFacts(WorkerToolState own, Optional<OrderKey> freeSlot, SlotState slotState, ToolRequirement.Mode mode,
		Optional<SelfCraft> saved, Optional<CancelReason> cancel, Optional<PendingFetch> fetching,
		SlotPresence fetchPresence, boolean atBoundary, boolean jobIdle,
		boolean smithServes, long slotOpenWorkTicks, boolean storedToolAvailable, boolean campHasSpace,
		Optional<CraftPlan> plan, long dayTime) {}
