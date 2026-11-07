package dev.villageheart.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * A server-side player without a client (M24's second player in the 2-player session): a real {@link ServerPlayer} joined through
 * {@code PlayerList.placeNewPlayer} with an {@link EmbeddedChannel}, so Fabric's JOIN event runs and the world sees a player.
 *
 * <p>Its connection is not in the server's connection list, so the server never ticks it: no keep-alive is sent or expected
 * (the simulation owner has run for hours this way), and nothing is read from the channel. The only upkeep is to free the outbound
 * queue (chunks, entity updates), which a server tick listener does for every fake player that joined and has not left.
 */
public final class FakePlayer {
	private static final List<FakePlayer> ACTIVE = new CopyOnWriteArrayList<>();
	private static boolean listening;

	public final ServerPlayer player;
	private final EmbeddedChannel channel;

	private FakePlayer(ServerPlayer player, EmbeddedChannel channel) {
		this.player = player;
		this.channel = channel;
	}

	/**
	 * A connection that drops everything the server sends. Fabric's client-gametest NetworkSynchronizer counts every packet
	 * sent over a clientbound connection as "in flight" until a client handles it; a fake player has no client, so the count would
	 * never return to zero and the real client's tick loop would die after 10 s ("Network synchronizer in invalid state").
	 * Dropping at {@code send} means nothing is counted and nothing queues up.
	 */
	private static final class SilentConnection extends Connection {
		SilentConnection() { super(PacketFlow.SERVERBOUND); }

		@Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}

		@Override public void send(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener listener) {}

		@Override public void send(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener listener,
				boolean flush) {}
	}

	/** Joins on the server thread (call it from {@code computeOnServer} or a server tick). */
	public static FakePlayer join(MinecraftServer server, ServerLevel level, UUID id, String name, GameType mode, BlockPos near) {
		GameProfile profile = new GameProfile(id, name);
		ServerPlayer p = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
		Connection conn = new SilentConnection();
		EmbeddedChannel channel = new EmbeddedChannel(conn);
		server.getPlayerList().placeNewPlayer(conn, p, CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(mode);
		p.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		FakePlayer f = new FakePlayer(p, channel);
		synchronized (FakePlayer.class) {
			if (!listening) {
				listening = true;
				ServerTickEvents.END_SERVER_TICK.register(s -> {
					for (FakePlayer a : ACTIVE) a.channel.releaseOutbound();
				});
			}
		}
		ACTIVE.add(f);
		return f;
	}

	/** Leaves the server (server thread). */
	public void leave(MinecraftServer server) {
		ACTIVE.remove(this);
		server.getPlayerList().remove(player);
		channel.releaseOutbound();
	}

	/** True while the server still lists this player. */
	public boolean online(MinecraftServer server) {
		return server.getPlayerList().getPlayer(player.getUUID()) != null;
	}
}
