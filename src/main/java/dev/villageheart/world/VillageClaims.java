package dev.villageheart.world;

import dev.villageheart.VillageHeart;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.ClaimRules;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** Server-thread glue between Minecraft and the pure claim rules in dev.villageheart.core. */
public final class VillageClaims {
	private VillageClaims() {}

	public static VillageSavedData data(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(VillageSavedData.TYPE);
	}

	public static Optional<VillageState> current(MinecraftServer server) {
		return data(server).village();
	}

	public static HeartLocation locate(ServerLevel level, BlockPos pos) {
		return new HeartLocation(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
	}

	/** Distance to the nearest village bell within the claim range, or -1. */
	public static double bellDistance(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> bell = level.getPoiManager().findClosest(
				type -> type.is(PoiTypes.MEETING), pos, ClaimRules.MAX_BELL_DISTANCE, PoiManager.Occupancy.ANY);
		return bell.map(b -> Math.sqrt(b.distSqr(pos))).orElse(-1.0);
	}

	/** Read-only claim check for a Heart at pos. */
	public static ClaimRules.Result evaluate(ServerLevel level, BlockPos pos) {
		return data(level.getServer()).evaluate(bellDistance(level, pos));
	}

	/** The only source of placement refusals: heals a stale claim first, then evaluates. */
	public static ClaimRules.Result checkPlacement(ServerLevel level, BlockPos pos, @Nullable Player player) {
		if (player == null) return ClaimRules.Result.NO_BELL_NEARBY;
		healStaleClaim(level.getServer());
		return evaluate(level, pos);
	}

	public static ClaimRules.Result claim(ServerLevel level, BlockPos pos, Player owner) {
		ClaimRules.Result result = data(level.getServer()).claim(locate(level, pos), owner.getUUID(), owner.getGameProfile().name(),
				bellDistance(level, pos), level.getGameTime());
		if (result == ClaimRules.Result.CLAIMED) dev.villageheart.building.MarkerAdoption.adoptLoaded(level, pos);
		return result;
	}

	public static Component refusalMessage(ClaimRules.Result result, Player player) {
		if (result == ClaimRules.Result.ALREADY_HAVE_VILLAGE) {
			Optional<VillageState> v = current(player.level().getServer());
			if (v.isPresent() && !v.get().owner().equals(player.getUUID())) {
				return Component.translatable("message.villageheart.claim.already_claimed_other", v.get().ownerName());
			}
		}
		return Component.translatable("message.villageheart.claim." + result.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** Releases the claim if pos is the claimed Heart; drops the treasury there and tells the owner. */
	public static Optional<VillageState> releaseAt(ServerLevel level, BlockPos pos) {
		java.util.Set<HeartLocation> held = data(level.getServer()).reservedJobBlocks();
		// M19a: committed self-crafts are refunded before the claim (and its records) goes
		current(level.getServer()).filter(v -> v.isHeartAt(locate(level, pos))).ifPresent(v -> {
			dev.villageheart.worker.gear.SelfCrafts.refundAll(level.getServer());
			dev.villageheart.worker.smith.SmithCrafts.refundAll(level.getServer());
			dev.villageheart.worker.smith.GolemBuilds.refundAll(level.getServer());
		});
		if (current(level.getServer()).filter(v -> v.isHeartAt(locate(level, pos))).isPresent()) dev.villageheart.growth.Growth.onRelease(level.getServer()); // M23: give back the baby's bed first
		Optional<VillageState> released = data(level.getServer()).releaseAt(locate(level, pos));
		released.ifPresent(v -> {
			dev.villageheart.worker.JobSiteReservations.releaseAll(level.getServer(), held);
			dev.villageheart.worker.Workers.restoreAfterRelease(level.getServer(), v.workers());
			dev.villageheart.building.MarkerAdoption.resetReleased(level.getServer(), v);
			dropTreasury(level, pos, v.treasury());
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(v.owner());
			if (owner != null) owner.sendSystemMessage(Component.translatable("message.villageheart.heart.released"));
			VillageHeart.LOG.info("Village of {} released at {}", v.ownerName(), v.heart());
		});
		return released;
	}

	/** Test and command use: the same path as a player's deposit (clamped, and it pays the strikers it can). */
	public static void depositTreasury(MinecraftServer server, int emeralds) {
		dev.villageheart.economy.Treasury.deposit(server, emeralds, null);
	}

	public static void setRank(MinecraftServer server, UUID player, Rank rank) {
		// the online player's profile name; a blank name keeps the one the village already knows (or a placeholder)
		ServerPlayer online = server.getPlayerList().getPlayer(player);
		String name = online == null ? "" : online.getGameProfile().name();
		long now = server.overworld().getGameTime();
		data(server).update(v -> v.withRank(player, name, rank, now));
	}

	/**
	 * If the stored Heart's chunk is loaded but no Village Heart stands there, the claim is stale (the block was
	 * removed without neighbour updates). Release it, dropping the treasury there. Never loads chunks.
	 */
	public static void healStaleClaim(MinecraftServer server) {
		Optional<VillageState> village = current(server);
		if (village.isEmpty()) return;
		HeartLocation h = village.get().heart();
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(h.dimension())));
		if (level == null) return;
		BlockPos pos = new BlockPos(h.x(), h.y(), h.z());
		if (!level.isLoaded(pos)) return;
		if (level.getBlockState(pos).is(ModBlocks.VILLAGE_HEART)) return;
		releaseAt(level, pos);
	}

	/** Spawns the treasury as emerald stacks. Not Block.popResource: that obeys the block-drops gamerule. */
	static void dropTreasury(ServerLevel level, BlockPos pos, int emeralds) {
		int left = emeralds;
		while (left > 0) {
			int n = Math.min(64, left);
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.EMERALD, n));
			left -= n;
		}
	}
}
