package dev.villageheart.gametest.raid;

import dev.villageheart.gametest.mixin.RaidTestAccess;
import dev.villageheart.mixin.RaidsView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * A raid a test or the sim starts itself (M25 plan 3.2 #19). It refuses to extend a raid that is already there,
 * and it refuses a spot vanilla would not treat as a village.
 */
public final class RaidStart {
	private RaidStart() {}

	public static Raid start(ServerPlayer player, BlockPos at, int omen) {
		ServerLevel level = player.level();
		if (!level.getGameRules().get(GameRules.RAIDS)) throw new IllegalStateException("raids are off");
		if (level.getDifficulty() == Difficulty.PEACEFUL) throw new IllegalStateException("difficulty is peaceful");
		if (level.getRaidAt(at) != null) throw new IllegalStateException("a raid is already at " + at);
		if (!level.isVillage(at)) throw new IllegalStateException(at + " is not a village");
		Raid raid = level.getRaids().createOrExtendRaid(player, at);
		if (raid == null) throw new IllegalStateException("createOrExtendRaid returned null");
		raid.setRaidOmenLevel(omen);
		level.getRaids().setDirty();
		level.getRaids().getId(raid).ifPresent(id -> dev.villageheart.raid.VillageRaids.mark(id, dev.villageheart.core.raid.RaidOrigin.TEST,
				new dev.villageheart.core.raid.RaidPlan(Math.max(1, Math.min(5, omen)), 0, 0, 0, false)));
		return raid;
	}

	/** Keeps the next wave from spawning: the cooldown is put back to 300 ticks whenever a test calls this. */
	public static void holdWaves(Raid raid) {
		((RaidTestAccess) raid).villageheart$setRaidCooldownTicks(300);
	}

	/** Stops every raid whose centre is within {@code radius} blocks, and discards its raiders. */
	public static void stopAll(ServerLevel level, BlockPos pos, int radius) {
		double r2 = (double) radius * radius;
		List<Raid> hits = new ArrayList<>();
		for (Raid raid : ((RaidsView) level.getRaids()).villageheart$raidMap().values()) {
			if (raid.getCenter().distSqr(pos) <= r2) hits.add(raid);
		}
		for (Raid raid : hits) {
			for (Raider raider : List.copyOf(raid.getAllRaiders())) raider.discard();
			raid.stop();
		}
	}
}
