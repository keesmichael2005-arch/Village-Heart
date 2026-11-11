package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import net.minecraft.server.MinecraftServer;

/**
 * At most {@link Balance#MAX_SIEGE_PATHS_PER_TICK} siege path searches per server tick. A mob that does not get a token
 * tries again on its next check. One budget for the whole server: the lure and the breach goals share it.
 */
public final class PathBudget {
	private static int tick = Integer.MIN_VALUE;
	private static int used;

	private PathBudget() {}

	public static boolean take(MinecraftServer server) {
		int now = server.getTickCount();
		if (now != tick) {
			tick = now;
			used = 0;
		}
		if (used >= Balance.MAX_SIEGE_PATHS_PER_TICK) return false;
		used++;
		return true;
	}

	public static int usedThisTick(MinecraftServer server) {
		return server.getTickCount() == tick ? used : 0;
	}

	public static void resetForTests() {
		tick = Integer.MIN_VALUE;
		used = 0;
	}
}
