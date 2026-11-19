package dev.villageheart.worker;

import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.WeatherShelter;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The saved weather flag covers rain, snow, and thunderstorms. It is not the fading rain level: that stays
 * low for a second after the weather changes, and the work stops and starts with the flag.
 *
 * <p>A GameTest server runs many tests in one world, so one test's {@code setWeatherParameters} would stop
 * every other worker. {@link #force} marks only the villagers that test hired. A real sky still uses the flag.
 */
public final class WorkWeather {
	private static final Set<UUID> FORCED = ConcurrentHashMap.newKeySet();

	private WorkWeather() {}

	/** The shared GameTest world must not be set to rain. Mark this villager instead. */
	public static void force(Villager v, boolean raining) {
		if (raining) FORCED.add(v.getUUID());
		else FORCED.remove(v.getUUID());
	}

	public static boolean precipitating(ServerLevel level, Villager v) {
		return level.getLevelData().isRaining() || FORCED.contains(v.getUUID());
	}

	public static boolean waiting(ServerLevel level, Villager v) {
		return WeatherShelter.workerWaits(precipitating(level, v), DaySchedule.phaseAt(level.getDayTime()));
	}
}
