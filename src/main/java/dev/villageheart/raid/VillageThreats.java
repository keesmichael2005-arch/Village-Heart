package dev.villageheart.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.worker.guard.RaidCheck;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;

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

	/** A raid is tracked or a siege is running. */
	public static boolean underAttack(MinecraftServer server) {
		return VillageClaims.current(server).map(v -> v.threats().underAttack()).orElse(false);
	}

	/**
	 * The tracked raid is still running and {@code pos} is within {@link Balance#RAID_REACH} of its centre.
	 * A raid reaches 96 blocks; a worker on another test plot, or one left behind after the raid stopped, keeps working.
	 */
	public static boolean nearRaid(ServerLevel level, BlockPos pos) {
		var state = VillageClaims.current(level.getServer());
		if (state.isEmpty() || state.get().threats().raid().isEmpty()) return false;
		Raid live = level.getRaids().get(state.get().threats().raid().get().vanillaId());
		if (live == null || live.isStopped()) return false;
		return live.getCenter().distSqr(pos) <= (double) Balance.RAID_REACH * Balance.RAID_REACH;
	}

	public static boolean forGuards(ServerLevel level, VillageState village) {
		if (village.threats().underAttack()) return true;
		return previous.underRaid(level, village);
	}
}
