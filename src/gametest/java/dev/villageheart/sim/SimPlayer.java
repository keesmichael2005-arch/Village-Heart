package dev.villageheart.sim;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
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
 * The simulated owner: a creative fake player that stays at the Heart (so mobs spawn and random ticks run as for a
 * real player standing there). Its fake connection keeps every outgoing packet; {@link #drain()} frees them.
 */
final class SimPlayer {
	final ServerPlayer player;
	private final EmbeddedChannel channel;

	private SimPlayer(ServerPlayer player, EmbeddedChannel channel) {
		this.player = player;
		this.channel = channel;
	}

	static SimPlayer join(MinecraftServer server, ServerLevel level, BlockPos near) {
		GameProfile profile = new GameProfile(SimWorld.OWNER_UUID, SimWorld.OWNER_NAME);
		ServerPlayer p = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
		Connection conn = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(conn);
		server.getPlayerList().placeNewPlayer(conn, p, CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(GameType.CREATIVE);
		p.teleportTo(near.getX() + 0.5, near.getY() + 1, near.getZ() + 1.5);
		return new SimPlayer(p, channel);
	}

	/** Without this the fake channel's outbound queue grows forever (chunks, entity updates). */
	void drain() {
		channel.releaseOutbound();
	}
}
