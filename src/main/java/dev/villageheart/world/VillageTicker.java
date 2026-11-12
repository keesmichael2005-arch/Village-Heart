package dev.villageheart.world;

import dev.villageheart.core.sim.TickTimer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Times all mod per-tick work. Rule: per-tick work runs either in a {@link VillageSystem} or inside a
 * {@link #section()}; anything else escapes the 1 ms/tick budget measurement.
 */
public final class VillageTicker {
	public static final TickTimer TIMER = new TickTimer();
	/** Overworld entity ticking, one sample per server tick (M25 3.17). */
	public static final TickTimer ENTITIES = new TickTimer();

	private VillageTicker() {}

	public static final class Section implements AutoCloseable {
		private final long start = System.nanoTime();

		@Override
		public void close() {
			TIMER.add(System.nanoTime() - start);
		}
	}

	/** try (var t = VillageTicker.section()) { ... } */
	public static Section section() {
		return new Section();
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VillageTicker::onEndTick);
	}

	static void onEndTick(MinecraftServer server) {
		try (Section t = section()) {
			VillageSystems.tickAll(server);
		}
		TIMER.commitTick();
		ENTITIES.commitTick();
	}
}
