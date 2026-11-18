package dev.villageheart.worker;

import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.WeatherShelter;
import net.minecraft.server.level.ServerLevel;

/** The level's rain flag covers rain, snow, and thunderstorms. Applied to {@link WeatherShelter}. */
public final class WorkWeather {
	private WorkWeather() {}

	public static boolean waiting(ServerLevel level) {
		return WeatherShelter.workerWaits(level.isRaining(), DaySchedule.phaseAt(level.getDayTime()));
	}
}
