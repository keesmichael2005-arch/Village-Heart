package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.ToolRequirement;

/** The hand-off between a worker's own craft and a smith (M19a plan 3.8). */
public final class SupplyRule {
	private SupplyRule() {}

	/**
	 * May this worker start a self-craft for this slot now? Without a serving smith, always. With one, only for a MISSING slot of a REQUIRED
	 * tool that has had no open order for {@code SELF_CRAFT_FALLBACK_TICKS} work ticks.
	 */
	public static boolean selfCraftMayStart(boolean smithServes, SlotState state, ToolRequirement.Mode mode, long slotOpenWorkTicks) {
		if (!smithServes) return true;
		return state == SlotState.MISSING && mode == ToolRequirement.Mode.REQUIRED && slotOpenWorkTicks >= Balance.SELF_CRAFT_FALLBACK_TICKS;
	}
}
