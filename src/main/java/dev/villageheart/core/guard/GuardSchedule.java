package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;

/**
 * The guard day (M18 3.3). DAY shift: duty [0, 12000), off [12000, 13000), sleep [13000, 24000). NIGHT shift: off [0, 1000), sleep
 * [1000, 12000), duty [12000, 24000). Off comes right after each guard's own duty, so each hands its gear in at the end of its duty.
 * Overrides, first wins: wounded -> SLEEP (even in a raid); raid or alarm -> DUTY; no work (strike, starving) turns DUTY (only) into OFF.
 */
public final class GuardSchedule {
	private GuardSchedule() {}


	public static GuardPhase phase(Shift shift, long dayTime, boolean raid, boolean wounded, boolean canWork) {
		return phase(shift, dayTime, raid, false, wounded, canWork);
	}

	/**
	 * With the alarm (M18 r1): a hostile attacked a villager inside the claim, so every guard that is not wounded comes on duty until the
	 * alarm ends (the glue holds it for a while so it does not flap). Wounded still comes first.
	 */
	public static GuardPhase phase(Shift shift, long dayTime, boolean raid, boolean alarm, boolean wounded, boolean canWork) {
		if (wounded) return GuardPhase.SLEEP;
		if (raid || alarm) return GuardPhase.DUTY;
		GuardPhase p = base(shift, Math.floorMod(dayTime, (long) Balance.DAY_TICKS));
		return p == GuardPhase.DUTY && !canWork ? GuardPhase.OFF : p;
	}

	/**
	 * With the training day (M18 r3): a guard that is not yet qualified is never on duty and never sent to bed by the clock, it trains (OFF,
	 * awake, sparring at the Training Yard) until it qualifies at dusk. Wounded still comes first. Nothing calls a trainee up, not even a raid.
	 */
	public static GuardPhase phase(Shift shift, long dayTime, boolean raid, boolean alarm, boolean wounded, boolean canWork, boolean training) {
		if (training && !wounded) return GuardPhase.OFF;
		return phase(shift, dayTime, raid, alarm, wounded, canWork);
	}

	private static GuardPhase base(Shift shift, long t) {
		int duty = Balance.GUARD_DUTY_TICKS, off = Balance.GUARD_OFF_TICKS;
		if (shift == Shift.DAY) {
			long start = Balance.DAY_DUTY_START;
			if (t >= start && t < start + duty) return GuardPhase.DUTY;
			if (t >= start + duty && t < start + duty + off) return GuardPhase.OFF;
			return GuardPhase.SLEEP;
		}
		if (t >= Balance.NIGHT_DUTY_START) return GuardPhase.DUTY;
		if (t < off) return GuardPhase.OFF;
		return GuardPhase.SLEEP;
	}
}
