package dev.villageheart.raid;

import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.raid.RaidSummary;
import dev.villageheart.world.VillageClaims;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sends a raid line to the online owner, stewards and residents. A test can replace the sink. */
public final class RaidNotices {
	@FunctionalInterface
	public interface Sink { void line(ServerPlayer player, String text); }

	private static final Sink DEFAULT = (player, text) -> player.sendSystemMessage(Component.literal(text));
	private static Sink sink = DEFAULT;

	private RaidNotices() {}

	public static void setSinkForTest(Sink next) { sink = next == null ? DEFAULT : next; }

	public static void started(MinecraftServer server) { tell(server, name -> dev.villageheart.core.raid.RaidNotices.start(name)); }

	public static void ended(MinecraftServer server, RaidSummary summary) {
		tell(server, name -> dev.villageheart.core.raid.RaidNotices.end(name, summary.outcome(), summary.villagerDeaths()));
	}

	/** The 600-tick wait for a postponed raid has begun. */
	public static void gathering(MinecraftServer server) {
		tell(server, dev.villageheart.core.raid.RaidNotices::gathering);
	}

	/** A random victory paid the treasury. */
	public static void paid(MinecraftServer server, int emeralds) {
		tell(server, name -> dev.villageheart.core.raid.RaidNotices.paid(name, emeralds));
	}

	private static void tell(MinecraftServer server, java.util.function.Function<String, String> text) {
		var state = VillageClaims.current(server);
		if (state.isEmpty()) return;
		VillageState village = state.get();
		Map<UUID, Rank> ranks = new HashMap<>(village.ranks());
		ranks.put(village.owner(), Rank.OWNER);
		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) online.add(player.getUUID());
		String line = text.apply(village.ownerName());
		for (UUID id : dev.villageheart.core.raid.RaidNotices.audience(ranks, online)) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player != null) sink.line(player, line);
		}
	}
}
