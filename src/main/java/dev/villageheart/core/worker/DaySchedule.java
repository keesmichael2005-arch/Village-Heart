package dev.villageheart.core.worker;

import dev.villageheart.core.Balance;

/** Pure day-time to phase mapping: WAKE [0, WORK_START), WORK [WORK_START, WORK_END), GO_HOME [WORK_END, SLEEP_START), SLEEP [SLEEP_START, DAY_TICKS). */
public final class DaySchedule {
	private DaySchedule() {}

	public static DayPhase phaseAt(long dayTime) {
		long t = Math.floorMod(dayTime, (long) Balance.DAY_TICKS);
		if (t < Balance.WORK_START) return DayPhase.WAKE;
		if (t < Balance.WORK_END) return DayPhase.WORK;
		if (t < Balance.SLEEP_START) return DayPhase.GO_HOME;
		return DayPhase.SLEEP;
	}
}
