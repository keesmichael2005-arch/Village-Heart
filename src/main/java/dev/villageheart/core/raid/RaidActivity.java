package dev.villageheart.core.raid;

import dev.villageheart.core.worker.DayPhase;

/** When a hired worker leaves its job and goes indoors (M25 plan 3.2 #6). */
public final class RaidActivity {
	private RaidActivity() {}

	public static boolean shelter(boolean underAttack, DayPhase phase, boolean away, boolean hasHome) {
		if (!underAttack || away) return false;
		if (phase == DayPhase.SLEEP) return !hasHome;
		return true;
	}
}
