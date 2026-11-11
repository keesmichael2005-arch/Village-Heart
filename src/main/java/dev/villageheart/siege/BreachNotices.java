package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageClaims;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Chat when a wall first opens and when the last hole closes (docs/plans/M25.md 3.15). At most one breach line per
 * {@link Balance#BREACH_NOTICE_TICKS}. Residents and above hear it. The sink is the test seam.
 */
public final class BreachNotices {
	private static Consumer<String> sink;
	private static int lastCount = -1;
	private static long lastBreachTick = Long.MIN_VALUE;

	private BreachNotices() {}

	public static void setSinkForTest(Consumer<String> s) { sink = s; }

	public static void resetForTests() {
		sink = null;
		lastCount = -1;
		lastBreachTick = Long.MIN_VALUE;
	}

	public static void observe(MinecraftServer server) {
		VillageState village = VillageClaims.current(server).orElse(null);
		int n = village == null ? 0 : SiegeDamage.damage(server).breached().size();
		if (n > 0 && lastCount <= 0) {
			long now = server.getTickCount();
			if (lastBreachTick == Long.MIN_VALUE || now - lastBreachTick >= Balance.BREACH_NOTICE_TICKS) {
				say(server, village, "The wall of " + name(village) + " was breached");
				lastBreachTick = now;
			}
		} else if (n == 0 && lastCount > 0) {
			say(server, village, "The walls of " + name(village) + " are repaired");
		}
		lastCount = n;
	}

	private static String name(VillageState village) {
		if (village == null || village.ownerName().isBlank()) return "the village";
		return village.ownerName();
	}

	private static void say(MinecraftServer server, VillageState village, String text) {
		if (sink != null) sink.accept(text);
		if (village == null) return;
		Component line = Component.literal(text);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (village.rankOf(player.getUUID()).atLeast(Rank.RESIDENT)) player.sendSystemMessage(line);
		}
	}
}
