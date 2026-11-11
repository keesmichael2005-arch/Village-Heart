package dev.villageheart.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat lines and titles for the players in or near the claim: the raid and siege warnings, the start title, and the
 * stuck-raider lines (owner, 2026-11-11). Players are every non-spectator within {@link Balance#THREAT_PLAYER_RANGE}
 * of the Heart, not only ranked members: the threat is for whoever is there. All words are lang keys. A test can replace the sink.
 */
public final class ThreatMessages {
	public interface Sink {
		void chat(ServerPlayer player, String langKey);

		void title(ServerPlayer player, String langKey);
	}

	private static final Sink DEFAULT = new Sink() {
		@Override
		public void chat(ServerPlayer player, String langKey) { player.sendSystemMessage(Component.translatable(langKey)); }

		@Override
		public void title(ServerPlayer player, String langKey) {
			if (player.connection == null) return;
			player.connection.send(new ClientboundSetTitlesAnimationPacket(Balance.THREAT_TITLE_FADE_IN, Balance.THREAT_TITLE_STAY,
					Balance.THREAT_TITLE_FADE_OUT));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(langKey)));
		}
	};
	private static Sink sink = DEFAULT;

	private ThreatMessages() {}

	public static void setSinkForTest(Sink next) { sink = next == null ? DEFAULT : next; }

	public static void chat(MinecraftServer server, ThreatWarnings.Line line) {
		for (ServerPlayer player : near(server)) sink.chat(player, line.key());
	}

	public static void title(MinecraftServer server, ThreatWarnings.Line line) {
		for (ServerPlayer player : near(server)) sink.title(player, line.key());
	}

	/** The players who get a threat line: awake in the Heart's level, within the threat player range of it. */
	public static List<ServerPlayer> near(MinecraftServer server) {
		var state = VillageClaims.current(server);
		if (state.isEmpty()) return List.of();
		HeartLocation heart = state.get().heart();
		ServerLevel level = ThreatScheduler.levelOf(server, heart);
		if (level == null) return List.of();
		List<ServerPlayer> out = new ArrayList<>();
		double r2 = (double) Balance.THREAT_PLAYER_RANGE * Balance.THREAT_PLAYER_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double dx = player.getX() - heart.x(), dy = player.getY() - heart.y(), dz = player.getZ() - heart.z();
			if (dx * dx + dy * dy + dz * dz <= r2) out.add(player);
		}
		return out;
	}
}
