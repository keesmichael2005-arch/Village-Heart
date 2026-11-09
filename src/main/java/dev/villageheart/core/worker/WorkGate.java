package dev.villageheart.core.worker;

/** The one question every job asks before it works (M12 plan 3.2 #7): may this worker work at all? False when hungry for two days or unpaid. */
public final class WorkGate {
	private WorkGate() {}

	public static boolean mayWork(Needs needs) { return needs.speed() > 0; }
}
