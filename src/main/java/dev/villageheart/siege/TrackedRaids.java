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
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;

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
		// GameTests share one world. A finished test leaves its raid (found within 96) and its occupied bell (averaged
		// within 64). Either one puts this raid's centre on somebody else's bell, and the raider then chops the wrong face.
		isolateCentre(level, center);
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

	/**
	 * Test stand-in for a raid whose waves are held. The vanilla raid would spawn a group on its next tick; this pushes that
	 * timer out so a repair test can watch the builder shelter without a wave arriving.
	 */
	public static void holdWaves(Raid raid) {
		try {
			var cooldown = Raid.class.getDeclaredField("raidCooldownTicks");
			cooldown.setAccessible(true);
			cooldown.setInt(raid, 1_000_000);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not hold the raid's waves", e);
		}
	}

	/** Stops the vanilla raid and clears the tracked record, so workers leave shelter on the next brain update. */
	public static void stop(MinecraftServer server) {
		var village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		village.get().threats().raid().ifPresent(record -> {
			for (ServerLevel level : server.getAllLevels()) {
				Raid raid = level.getRaids().get(record.vanillaId());
				if (raid != null && !raid.isStopped()) raid.stop();
			}
		});
		VillageClaims.data(server).update(v -> v.withThreats(v.threats().withRaid(Optional.empty())));
	}

	/** {@code getNearbyRaid} compares this value to a distance squared. 96 blocks, same as {@code ServerLevel.getRaidAt}. */
	private static final int RAID_NEIGHBOUR_SQR = 96 * 96;

	private static void isolateCentre(ServerLevel level, BlockPos center) {
		for (int n = 0; n < 16; n++) {
			Raid nearby = level.getRaids().getNearbyRaid(center, RAID_NEIGHBOUR_SQR);
			if (nearby == null) break;
			nearby.stop();
		}
		var occupied = level.getPoiManager().getInRange(type -> type.is(PoiTypeTags.VILLAGE), center, 64, PoiManager.Occupancy.IS_OCCUPIED).toList();
		for (var rec : occupied) {
			BlockPos pos = rec.getPos();
			if (pos.equals(center)) continue;
			// Setting the block to air lets vanilla drop the POI. remove() while the block stands logs a mismatch.
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}
}
