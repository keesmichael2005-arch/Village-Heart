package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * At most {@link Balance#MAX_SIEGE_PATHS_PER_TICK} siege path searches per server tick. A mob that does not get a token
 * tries again on its next check. One budget for the whole server: the lure and the breach goals share it.
 *
 * <p>The entity list is a stable order, so the same two mobs would otherwise take both tokens every tick and a zombie
 * further down the list would never search. A mob that was refused is served before one that searched last tick.
 */
public final class PathBudget {
	private static int tick = Integer.MIN_VALUE;
	private static int used;
	private static Set<UUID> owed = new HashSet<>();
	private static Set<UUID> denied = new HashSet<>();

	private PathBudget() {}

	public static boolean take(MinecraftServer server, UUID mob) {
		int now = server.getTickCount();
		if (now != tick) {
			tick = now;
			used = 0;
			owed = denied;
			denied = new HashSet<>();
		}
		if (used >= Balance.MAX_SIEGE_PATHS_PER_TICK || (!owed.isEmpty() && !owed.contains(mob))) {
			denied.add(mob);
			return false;
		}
		owed.remove(mob);
		used++;
		return true;
	}

	public static int usedThisTick(MinecraftServer server) {
		return server.getTickCount() == tick ? used : 0;
	}

	public static void resetForTests() {
		tick = Integer.MIN_VALUE;
		used = 0;
		owed = new HashSet<>();
		denied = new HashSet<>();
	}
}
