package dev.villageheart.gametest.mixin;

import net.fabricmc.fabric.impl.client.gametest.util.ClientGameTestImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Gives a client test's world load more time (fabric-client-gametest-api-v1 4.3.5; see docs/TESTING.md, "Layer 4: the world-load
 * timeout").
 *
 * <p>{@code ClientGameTestImpl.waitForWorldLoad} (called by every {@code worldBuilder().create()}, {@code createServer} and
 * {@code TestWorldSave.open}) polls "a level exists and the loading screen is gone" for {@code SharedConstants.TICKS_PER_MINUTE}
 * (1200) client ticks and then throws {@code AssertionError: Timeout loading world}. It is the right readiness signal, but the bound
 * is a number of ticks for something that takes wall-clock time: the integrated server prepares its spawn area ("Preparing spawn
 * area: 16%") on the chunk system's worker threads. On a machine with several Minecraft servers and clients at load 10 or more that
 * took longer than a minute (one run sat at 16% for about two minutes), and the run died before any test code ran, although nothing
 * was wrong. A world that really never loads still fails: after {@link #TICKS} ticks instead of 1200.
 */
@Mixin(ClientGameTestImpl.class)
abstract class WorldLoadTimeoutFix {
	private static final Logger VH_LOGGER = LoggerFactory.getLogger("villageheart-gametest");
	/** Five times Fabric's bound: 5 minutes at the nominal 20 ticks per second, more when ticks are slow. */
	private static final int TICKS = 6000;

	static {
		VH_LOGGER.info("WORLD_LOAD_TIMEOUT_FIX installed in ClientGameTestImpl.waitForWorldLoad (world load bound {} client ticks instead of 1200)", TICKS);
	}

	@ModifyConstant(method = "waitForWorldLoad", constant = @Constant(intValue = 1200))
	private static int villageheart$longerWorldLoad(int fabricBound) {
		return TICKS;
	}
}
