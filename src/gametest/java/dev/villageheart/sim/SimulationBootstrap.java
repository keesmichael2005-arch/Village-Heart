package dev.villageheart.sim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.nio.file.Path;

/**
 * "main" entrypoint of the gametest mod. A no-op unless the Loom run config "simulation" set
 * -Dvillageheart.sim.request=<file> (scripts/simulate.sh writes that file). See docs/TESTING.md, "Layer 3".
 */
public final class SimulationBootstrap implements ModInitializer {
	public static final String REQUEST_PROPERTY = "villageheart.sim.request";

	@Override
	public void onInitialize() {
		String request = System.getProperty(REQUEST_PROPERTY);
		if (request == null) return;
		SimulationRun run = SimulationRun.start(Path.of(request));
		ServerLifecycleEvents.SERVER_STARTED.register(run::onServerStarted);
		ServerTickEvents.END_SERVER_TICK.register(run::onTick);
		// Deliberately not guarded by the run's try/catch: the "crash" action must really crash the server.
		ServerTickEvents.END_SERVER_TICK.register(SimActions::crashTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(run::onServerStopped);
	}
}
