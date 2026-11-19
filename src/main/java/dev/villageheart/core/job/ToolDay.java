package dev.villageheart.core.job;

import dev.villageheart.core.guard.GuardPhase;
import dev.villageheart.core.guard.GuardSchedule;
import dev.villageheart.core.guard.Shift;

/**
 * What happens to the tool across the day. Morning (wake) it is taken out of the worker's chest. At the end of work it goes back, unless
 * the chest has no free slot, in which case it stays in the pocket overnight. A death puts it back and the cargo drops.
 */
public final class ToolDay {
	private ToolDay() {}

	public enum Morning { TAKE, ALREADY, NONE }

	public enum Evening { RETURN_THEN_DUMP, STAY_THEN_DUMP, DUMP }

	/** {@code toolToChest} is false when the worker was not holding one. The cargo always drops. */
	public record Death(boolean toolToChest, boolean dropCargo) {}

	public static Morning morning(boolean holdingTool, boolean chestHasTool) {
		if (holdingTool) return Morning.ALREADY;
		return chestHasTool ? Morning.TAKE : Morning.NONE;
	}

	public static Evening evening(boolean holdingTool, boolean chestHasFreeSlot) {
		if (!holdingTool) return Evening.DUMP;
		return chestHasFreeSlot ? Evening.RETURN_THEN_DUMP : Evening.STAY_THEN_DUMP;
	}

	public static Death onDeath(boolean holdingTool) { return new Death(holdingTool, true); }

	/** A night guard's off window is dawn. A day guard's is dusk. That is when the sword and armour go back in their chest. */
	public static boolean guardHandsIn(Shift shift, long dayTime) {
		return GuardSchedule.phase(shift, dayTime, false, false, true) == GuardPhase.OFF;
	}
}
