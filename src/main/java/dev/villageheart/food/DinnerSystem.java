package dev.villageheart.food;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.food.DinnerClock;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import java.util.OptionalLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Serves the village's dinner once a day at 11000 (M11 plan 3.2 #3). One day-time read per tick when nothing is due. */
public final class DinnerSystem implements VillageSystem {
	public static final DinnerSystem INSTANCE = new DinnerSystem();

	private DinnerSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (!NeedsOverride.dinnerEnabled) return;
		ServerLevel level = Dinner.levelOf(server, village);
		if (level == null) return;
		long dayTime = level.getDayTime();
		var data = VillageClaims.data(server);
		OptionalLong last = data.lastDinnerDay();
		if (last.isEmpty()) {
			// a new claim (or an old save): claiming at dusk must not serve an instant dinner
			data.setLastDinnerDay(DinnerClock.initialLastDay(dayTime));
			return;
		}
		if (!DinnerClock.due(dayTime, last.getAsLong())) return;
		Dinner.serve(level, village, DinnerClock.dayIndex(dayTime));
	}
}
