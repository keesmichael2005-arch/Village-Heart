package dev.villageheart.core.worker;

/**
 * Who leaves their bed for the bell. A villager already asleep stays asleep through the meeting and the walk home.
 * Guards never ask: their own shift decides, and a guard on duty is not in this day.
 */
public final class MeetingRules {
	private MeetingRules() {}

	public static boolean stayAsleep(DayPhase phase, boolean sleeping) {
		return sleeping && (phase == DayPhase.MEET || phase == DayPhase.GO_HOME);
	}
}
