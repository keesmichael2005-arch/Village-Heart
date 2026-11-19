package dev.villageheart.sim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.gamerules.GameRules;

import java.nio.file.Path;

/**
 * "main" entrypoint of the gametest mod. A no-op unless the Loom run config "simulation" set
 * -Dvillageheart.sim.request=<file> (scripts/simulate.sh writes that file). See docs/TESTING.md, "Layer 3".
 */
public final class SimulationBootstrap implements ModInitializer {
	public static final String REQUEST_PROPERTY = "villageheart.sim.request";

	@Override
	public void onInitialize() {
		SimTickets.register(); // also used by the L2 test of the follow tickets
		String request = System.getProperty(REQUEST_PROPERTY);
		if (request == null) {
			// One world for the whole suite. Rain now stops hired work. A test environment that sets
			// advance_weather puts the rule back to its default when the test ends, so a later test
			// can be rained on. The headless server stays clear. The client world is singleplayer and
			// sets its own sky for the rain frame.
			ServerTickEvents.END_SERVER_TICK.register(server -> {
				if (server.isSingleplayer()) return;
				var level = server.overworld();
				if (level == null) return;
				if (level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
					level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
				}
				if (level.getLevelData().isRaining()) level.setWeatherParameters(0, 0, false, false);
			});
			return;
		}
		SimulationRun run = SimulationRun.start(Path.of(request));
		ServerLifecycleEvents.SERVER_STARTED.register(run::onServerStarted);
		ServerTickEvents.END_SERVER_TICK.register(run::onTick);
		// Deliberately not guarded by the run's try/catch: the "crash" action must really crash the server.
		ServerTickEvents.END_SERVER_TICK.register(SimActions::crashTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(run::onServerStopped);
	}
}
