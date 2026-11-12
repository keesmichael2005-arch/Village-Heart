package dev.villageheart.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat lines (gold warnings, bold red starts) and titles for the players in or near the claim: the raid and siege warnings, the start title, and the
 * stuck-raider lines (owner, 2026-11-11). Players are every non-spectator within {@link Balance#THREAT_PLAYER_RANGE}
 * of the Heart, not only ranked members: the threat is for whoever is there. All words are lang keys. A test can replace the sink.
 */
public final class ThreatMessages {
	public interface Sink {
		void chat(ServerPlayer player, String langKey);

		/** The chat line with its look. A recording test sink overrides this to see the tone; the default drops the tone. */
		default void chat(ServerPlayer player, String langKey, ThreatWarnings.Tone tone) { chat(player, langKey); }

		void title(ServerPlayer player, String langKey);
	}

	private static final Sink DEFAULT = new Sink() {
		@Override
		public void chat(ServerPlayer player, String langKey) { chat(player, langKey, ThreatWarnings.Tone.NOTICE); }

		@Override
		public void chat(ServerPlayer player, String langKey, ThreatWarnings.Tone tone) {
			MutableComponent text = Component.translatable(langKey);
			switch (tone) {
				case WARNING -> text.withStyle(ChatFormatting.GOLD);
				case ALERT -> text.withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
				case NOTICE -> { }
			}
			player.sendSystemMessage(text);
		}

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
		chat(server, line, ThreatWarnings.Tone.NOTICE);
	}

	public static void chat(MinecraftServer server, ThreatWarnings.Line line, ThreatWarnings.Tone tone) {
		for (ServerPlayer player : near(server)) sink.chat(player, line.key(), tone);
	}

	/** A lead-up line before a raid or siege, in gold. */
	public static void warn(MinecraftServer server, ThreatWarnings.Kind kind, int index) {
		chat(server, kind.lines().get(index), kind.warningTone());
	}

	/**
	 * The start of a threat (owner, 2026-11-12): one bold red chat line for both kinds, and the big title for a raid only.
	 * The caller rings the bell.
	 */
	public static void started(MinecraftServer server, ThreatWarnings.Kind kind) {
		chat(server, kind.start(), kind.startTone());
		kind.title().ifPresent(title -> title(server, title));
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
