package dev.villageheart.worker;

import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.WeatherShelter;
import net.minecraft.server.level.ServerLevel;

/**
 * The saved weather flag covers rain, snow, and thunderstorms. It is not the fading rain level: that stays
 * low for a second after the weather changes, and the work stops and starts with the flag.
 */
public final class WorkWeather {
	private WorkWeather() {}

	public static boolean precipitating(ServerLevel level) {
		return level.getLevelData().isRaining();
	}

	public static boolean waiting(ServerLevel level) {
		return WeatherShelter.workerWaits(precipitating(level), DaySchedule.phaseAt(level.getDayTime()));
	}
}
