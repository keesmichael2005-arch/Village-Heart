package dev.villageheart.raid;

import dev.villageheart.core.VillageState;
import dev.villageheart.worker.guard.RaidCheck;
import dev.villageheart.world.VillageClaims;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * The one answer to "is the village under attack" (M25 plan 3.2). A tracked raid calls the guards.
 * Before the tracker has latched it, the previous raid check still answers, so a raid is not invisible
 * on the tick it is created.
 */
public final class VillageThreats {
	private static RaidCheck.Source previous = (level, village) -> false;

	private VillageThreats() {}

	public static void install() {
		previous = RaidCheck.setSource(VillageThreats::forGuards);
	}

	/** A raid is tracked. A siege joins this when the wall track adds one. */
	public static boolean underAttack(MinecraftServer server) {
		return VillageClaims.current(server).map(v -> v.threats().raid().isPresent()).orElse(false);
	}

	public static boolean forGuards(ServerLevel level, VillageState village) {
		if (village.threats().raid().isPresent()) return true;
		return previous.underRaid(level, village);
	}
}
