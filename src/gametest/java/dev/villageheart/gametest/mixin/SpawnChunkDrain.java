package dev.villageheart.gametest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the integrated server's spawn chunks moving while a client test waits for the loading screen.
 *
 * <p>{@code MinecraftServer.prepareLevels} and the player-spawn stage report "Preparing spawn area" and then wait for
 * chunk tasks. On a loaded CI runner those tasks sit unfinished at 16% until Fabric's world-load bound expires, and the
 * loading screen never leaves. {@code pollTask} runs one queued chunk task on the server thread. Draining a few of them
 * at the end of each wait lets a flat world's spawn finish instead of depending on a worker thread that is not getting
 * time. The loop stops as soon as the queue is empty, so a tick with nothing queued does one check.
 */
@Mixin(MinecraftServer.class)
abstract class SpawnChunkDrain {
	private static final Logger VH_LOGGER = LoggerFactory.getLogger("villageheart-gametest");
	private static final int PER_WAIT = 32;

	static {
		VH_LOGGER.info("SPAWN_CHUNK_DRAIN installed in MinecraftServer.waitUntilNextTick (up to {} chunk tasks per wait)", PER_WAIT);
	}

	@Inject(method = "waitUntilNextTick", at = @At("RETURN"))
	private void villageheart$drainSpawnChunks(CallbackInfo ci) {
		MinecraftServer server = (MinecraftServer) (Object) this;
		for (ServerLevel level : server.getAllLevels()) {
			int ran = 0;
			while (ran < PER_WAIT && level.getChunkSource().pollTask()) ran++;
		}
	}
}
