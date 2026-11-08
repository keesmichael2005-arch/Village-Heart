package dev.villageheart.economy;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.economy.PayResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/**
 * The shared dawn event (M12 plan 3.2 #3): fired once per day index change by the {@link WageSystem}, after wages, in registration order,
 * only while the Heart is loaded and with no catch-up over skipped days. M14 (tier goals) and M23 (breeding) register here. Server thread only.
 *
 * <p>The {@code village} a listener sees is after the wage run; {@link PayResult#treasuryBefore()} carries the value before it. When wages
 * are off (the GameTest default) the event still fires, with {@link PayResult#none}.
 */
public final class VillageDawn {
	private static final Map<String, DawnListener> LISTENERS = new LinkedHashMap<>();

	private VillageDawn() {}

	@FunctionalInterface
	public interface DawnListener {
		void onDawn(MinecraftServer server, VillageState village, long day, PayResult wages);
	}

	public interface Registration extends AutoCloseable {
		@Override
		void close();
	}

	public static Registration register(String id, DawnListener listener) {
		if (LISTENERS.putIfAbsent(id, listener) != null) throw new IllegalArgumentException("duplicate dawn listener: " + id);
		return () -> LISTENERS.remove(id, listener);
	}

	/** Runs the listeners; one that throws is logged and does not stop the others. */
	static void fire(MinecraftServer server, VillageState village, long day, PayResult wages) {
		for (Map.Entry<String, DawnListener> e : new ArrayList<>(LISTENERS.entrySet())) {
			try {
				e.getValue().onDawn(server, village, day, wages);
			} catch (RuntimeException ex) {
				VillageHeart.LOG.error("Dawn listener {} failed on day {}", e.getKey(), day, ex);
			}
		}
	}

	/** Test hook: drops every listener whose id starts with {@code test:} (a failed test never closed its registration). */
	public static void removeTestListeners() { LISTENERS.keySet().removeIf(id -> id.startsWith("test:")); }

	/** Ids of the registered listeners, in order (tests). */
	public static List<String> ids() { return new ArrayList<>(LISTENERS.keySet()); }
}
