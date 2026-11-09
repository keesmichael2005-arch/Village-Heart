package dev.villageheart.core.guard;

/**
 * What a guard does right now inside an activity (M18 3.3): a precedence table. On duty FIGHT > EQUIP > PATROL; off shift
 * DEFEND > RETURN_GEAR > SPAR > IDLE. EQUIP only while a planned upgrade exists and its source is reachable (not on cooldown); SPAR only with a
 * valid Training Yard linked to the guard's Barracks and a guard that may work.
 */
public final class GuardPlanner {
	private GuardPlanner() {}

	public enum DutyMode { FIGHT, EQUIP, PATROL }

	public enum OffMode { DEFEND, RETURN_GEAR, SPAR, IDLE }

	public record DutyFacts(boolean hasTarget, boolean upgradePlanned, boolean sourceReachable) {}

	public record OffFacts(boolean hasTarget, boolean gearToReturn, boolean yardValid, boolean canWork) {}

	public static DutyMode duty(DutyFacts f) {
		if (f.hasTarget()) return DutyMode.FIGHT;
		if (f.upgradePlanned() && f.sourceReachable()) return DutyMode.EQUIP;
		return DutyMode.PATROL;
	}

	public static OffMode off(OffFacts f) {
		if (f.hasTarget()) return OffMode.DEFEND;
		if (f.gearToReturn()) return OffMode.RETURN_GEAR;
		if (f.yardValid() && f.canWork()) return OffMode.SPAR;
		return OffMode.IDLE;
	}
}
