package dev.villageheart.core.worker;

/**
 * Rain, snow, and thunderstorms are one precipitation flag. During work time that flag sends a hired worker to stand
 * at the workplace and not work. An on-duty guard never waits. A guard who is asleep stays asleep. A guard who is
 * awake and not on duty (a trainee, or off shift) waits instead of sparring. Clearing the flag makes both answers
 * false, so work resumes while it is still work time. Wake, dinner, the walk home, and sleep are not this rule.
 */
public final class WeatherShelter {
	private WeatherShelter() {}

	public static boolean workerWaits(boolean precipitating, DayPhase phase) {
		return precipitating && phase == DayPhase.WORK;
	}

	/** {@code onDuty} is a guard on shift. {@code asleep} stays in bed. Anyone else who is a guard waits out the precipitation. */
	public static boolean guardWaits(boolean precipitating, boolean onDuty, boolean asleep) {
		return precipitating && !onDuty && !asleep;
	}
}
