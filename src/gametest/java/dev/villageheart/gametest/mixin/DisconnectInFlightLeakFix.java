package dev.villageheart.gametest.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.fabricmc.fabric.impl.client.gametest.threading.NetworkSynchronizer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes a leak of Fabric's clientbound in-flight packet counter when a singleplayer world closes (fabric-client-gametest-api-v1
 * 4.3.5; see docs/TESTING.md, "Layer 4 and Fabric's NetworkSynchronizer").
 *
 * <p>Fabric counts a clientbound packet at the HEAD of the server-side {@code Connection.sendPacket} and uncounts it when the
 * client's {@code Connection.channelRead0} returns. When a world closes, the client closes its end of the in-memory channel
 * ({@code ClientLevel.disconnect}) and then {@code Minecraft.disconnect} calls {@code NetworkSynchronizer.CLIENTBOUND.reset()}
 * (Fabric's {@code MinecraftMixin.onDisconnectCancelTasks}, at {@code dropAllTasks}). That is the only reset. But Netty closes
 * the server's end of a {@code LocalChannel} asynchronously, on the server channel's own event loop, and the integrated server
 * keeps ticking in parallel. Until that close runs, {@code Connection.isConnected()} is still true on the server, so every
 * packet the server sends in that window is counted after the reset, and its write then fails on the closed client end: it can
 * never reach {@code channelRead0}, so it is never uncounted. The next {@code waitForPacketHandlers} on the Render thread (the
 * first full client tick after the world closed) waits for those phantom packets, times out after 10 s and fails the run with
 * {@code AssertionError: Network synchronizer in invalid state}. Under CPU load the server's event loop runs late, so the window
 * grows; a busy server tick (M8's {@code heartTabs} kills 20 villagers just before closing) fills it with packets.
 *
 * <p>This re-applies Fabric's own reset at the point where it is actually true: in {@code Minecraft.disconnect}, after the
 * client has waited for the integrated server to stop ({@code isShutdown()}), and before the next tick. By then the client's
 * connection has been closed (no packet can be received any more) and the server thread has finished (no packet can be sent),
 * so any clientbound count left over is a leak by construction. It touches nothing else: during gameplay a packet that really
 * never arrives still fails after Fabric's 10 s. Every cleared leak is logged with {@code DISCONNECT_LEAK_CLEARED}.
 */
@Mixin(Minecraft.class)
abstract class DisconnectInFlightLeakFix {
	@Unique
	private static final Logger VH_LOGGER = LoggerFactory.getLogger("villageheart-gametest");

	static {
		VH_LOGGER.info("DISCONNECT_LEAK_FIX installed in Minecraft.disconnect (clears the clientbound in-flight count once the integrated server has stopped)");
	}

	@Shadow
	private @Nullable IntegratedServer singleplayerServer;

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
	private void villageheart$rememberIntegratedServer(CallbackInfo ci, @Share("vhServer") LocalRef<IntegratedServer> server) {
		server.set(singleplayerServer); // disconnect clears the field before it waits for the server to stop
	}

	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void villageheart$clearLeakedClientboundCount(CallbackInfo ci, @Share("vhServer") LocalRef<IntegratedServer> server) {
		IntegratedServer integratedServer = server.get();
		if (integratedServer == null || !integratedServer.isShutdown()) return; // no singleplayer world was closed here
		int leaked = ((NetworkSynchronizerAccessor) (Object) NetworkSynchronizer.CLIENTBOUND).villageheart$inFlightPackets().get();
		if (leaked <= 0) return;
		VH_LOGGER.warn("DISCONNECT_LEAK_CLEARED: {} clientbound packet(s) were counted as in flight after the world closed (sent by the "
				+ "integrated server to its already-closed connection); the unpatched code would stall the next tick for 10 s and fail the run", leaked);
		NetworkSynchronizer.CLIENTBOUND.reset();
	}
}
