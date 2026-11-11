package dev.villageheart.siege;

import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidPlan;
import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * Whether a raider belongs to the raid stored on the village. Track A owns the tracker that fills {@code threats.raid}
 * during play. {@link #track} is the test stand-in: it starts a vanilla raid and stores its id.
 */
public final class TrackedRaids {
	private TrackedRaids() {}

	public static boolean member(ServerLevel level, Raider raider) {
		Raid raid = raider.getCurrentRaid();
		if (raid == null || raid.isOver() || raid.isStopped()) return false;
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty() || village.get().threats().raid().isEmpty()) return false;
		OptionalInt id = level.getRaids().getId(raid);
		return id.isPresent() && id.getAsInt() == village.get().threats().raid().get().vanillaId();
	}

	/**
	 * The game-test server is created on Normal, but a reused world can still be Peaceful. A Peaceful raid calls
	 * {@code stop()} on its first tick, so the raider drops out of {@link #member}. Remember the old difficulty and put it back
	 * from the test reset.
	 */
	private static Difficulty restoreDifficulty;

	public static void restoreDifficulty(MinecraftServer server) {
		if (restoreDifficulty == null) return;
		server.setDifficulty(restoreDifficulty, true);
		restoreDifficulty = null;
	}

	/** Starts a vanilla raid at {@code center} and stores it as the village's tracked raid. Returns the vanilla raid. */
	public static Raid track(MinecraftServer server, ServerPlayer player, BlockPos center) {
		ServerLevel level = player.level();
		if (restoreDifficulty == null && level.getDifficulty() == Difficulty.PEACEFUL) {
			restoreDifficulty = Difficulty.PEACEFUL;
			server.setDifficulty(Difficulty.NORMAL, true);
		}
		if (!player.hasEffect(net.minecraft.world.effect.MobEffects.RAID_OMEN)) {
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RAID_OMEN, 6000));
		}
		Raid raid = level.getRaids().createOrExtendRaid(player, center);
		if (raid == null) throw new IllegalStateException("vanilla did not start a raid at " + center);
		OptionalInt id = level.getRaids().getId(raid);
		if (id.isEmpty()) throw new IllegalStateException("the raid has no id");
		int day = (int) (level.getGameTime() / 24000L);
		RaidRecord record = new RaidRecord(id.getAsInt(), RaidOrigin.TEST, RaidPlan.NONE, level.getGameTime(), day, 0, 0, 0, 0, 0, 1, 0, 0,
				false, 0, 0, 0, 0, -1L);
		VillageClaims.data(server).update(v -> v.withThreats(v.threats().withRaid(Optional.of(record))));
		return raid;
	}
}
