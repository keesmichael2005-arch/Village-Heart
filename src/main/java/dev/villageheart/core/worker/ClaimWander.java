package dev.villageheart.core.worker;

/**
 * Who wanders inside the claim instead of working. During the work day, a worker with no valid workplace and an unpaid striker both wander.
 * A guard wanders only while already off duty and awake: a raid still calls them up, and sleep still sends them to bed.
 */
public final class ClaimWander {
	private ClaimWander() {}

	public static boolean duringWork(DayPhase phase, boolean workplaceValid, boolean onStrike) {
		return phase == DayPhase.WORK && (!workplaceValid || onStrike);
	}

	public static boolean guard(boolean offDutyAndAwake, boolean workplaceValid, boolean onStrike) {
		return offDutyAndAwake && (!workplaceValid || onStrike);
	}
}
