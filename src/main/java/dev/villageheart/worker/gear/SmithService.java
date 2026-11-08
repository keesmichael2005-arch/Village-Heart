package dev.villageheart.worker.gear;

import dev.villageheart.core.VillageState;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * The seam to M19's blacksmith (M19a plan 3.8). No service is registered in M19a, so workers always make their own tools; M19 registers one
 * that says whether the village has a hired blacksmith at a valid Smithy. While it serves, no new self-craft starts (apart from the
 * fallback of {@code SupplyRule}).
 */
public interface SmithService {
	/** The registered service, if any. Tests set and clear it. */
	Holder CURRENT = new Holder();

	final class Holder {
		private volatile Optional<SmithService> value = Optional.empty();

		public Optional<SmithService> get() { return value; }

		public void set(SmithService s) { value = Optional.ofNullable(s); }

		public void clear() { value = Optional.empty(); }
	}

	boolean serves(MinecraftServer server, VillageState village);

	/** Is a smith serving this village now? */
	static boolean servesNow(MinecraftServer server, VillageState village) {
		return CURRENT.get().map(s -> s.serves(server, village)).orElse(false);
	}
}
