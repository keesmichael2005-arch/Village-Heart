package dev.villageheart.world;

import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered registry of {@link VillageSystem}s. Server thread only. */
public final class VillageSystems {
	private static final Map<String, VillageSystem> SYSTEMS = new LinkedHashMap<>();

	private VillageSystems() {}

	public interface Registration extends AutoCloseable {
		@Override
		void close();
	}

	public static Registration register(String id, VillageSystem system) {
		if (SYSTEMS.putIfAbsent(id, system) != null) throw new IllegalArgumentException("duplicate village system: " + id);
		return () -> SYSTEMS.remove(id, system);
	}

	/** No-op when there is no claim. */
	static void tickAll(MinecraftServer server) {
		if (SYSTEMS.isEmpty()) return;
		VillageClaims.current(server).ifPresent(v -> {
			for (VillageSystem s : SYSTEMS.values().toArray(new VillageSystem[0])) s.tick(server, v);
		});
	}
}
