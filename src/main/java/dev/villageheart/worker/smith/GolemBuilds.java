package dev.villageheart.worker.smith;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A golem build in progress, keyed by the Smithy marker. The materials are taken when it starts. Breaking the Smithy gives them back
 * and removes the entry in the same tick, so a refund cannot happen twice.
 */
public final class GolemBuilds {
	private GolemBuilds() {}

	public static void start(MinecraftServer server, HeartLocation smithy) {
		VillageClaims.data(server).update(v -> v.withGolemBuild(smithy, 0));
	}

	public static void progress(MinecraftServer server, HeartLocation smithy, int ticks) {
		VillageClaims.data(server).update(v -> v.golemProgress(smithy) < 0 ? v : v.withGolemBuild(smithy, ticks));
	}

	public static boolean finish(MinecraftServer server, HeartLocation smithy) {
		boolean[] removed = {false};
		VillageClaims.data(server).update(v -> {
			if (v.golemProgress(smithy) < 0) return v;
			removed[0] = true;
			return v.withoutGolemBuild(smithy);
		});
		return removed[0];
	}

	/**
	 * Returns 36 ingots and the pumpkin. Storage first, then a drop at the marker when that chunk is loaded. While the dimension
	 * is not loaded the entry stays, so the materials are not thrown away.
	 */
	public static void refund(MinecraftServer server, HeartLocation smithy) {
		ServerLevel level = Workers.levelOf(server, smithy);
		if (level == null) {
			VillageHeart.LOG.warn("Golem build at {}: the chunk is not loaded, the materials stay saved until it is", smithy);
			return;
		}
		boolean[] had = {false};
		VillageClaims.data(server).update(v -> {
			if (v.golemProgress(smithy) < 0) return v;
			had[0] = true;
			return v.withoutGolemBuild(smithy);
		});
		if (!had[0]) return;
		BlockPos at = new BlockPos(smithy.x(), smithy.y(), smithy.z());
		give(level, at, new ItemStack(Items.IRON_INGOT, Balance.GOLEM_INGOTS));
		give(level, at, new ItemStack(Items.CARVED_PUMPKIN, 1));
	}

	private static void give(ServerLevel level, BlockPos at, ItemStack stack) {
		if (Stockpiles.storageLevel(level.getServer()).isPresent()) Stockpiles.deposit(level, at, stack);
		if (!stack.isEmpty() && level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) != null) {
			Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, stack);
			stack = ItemStack.EMPTY;
		}
		if (!stack.isEmpty()) VillageHeart.LOG.warn("Golem refund at {}: {} {} left behind", at, stack.getCount(), stack.getItem());
	}

	public static void refundAll(MinecraftServer server) {
		var v = VillageClaims.current(server);
		if (v.isEmpty()) return;
		for (HeartLocation smithy : List.copyOf(v.get().golemBuilds().keySet())) refund(server, smithy);
	}

	/** A Smithy that is no longer registered gives its build back. An invalid-but-registered Smithy keeps the build paused. */
	public static void refundUnregistered(MinecraftServer server) {
		var v = VillageClaims.current(server);
		if (v.isEmpty()) return;
		for (HeartLocation smithy : List.copyOf(v.get().golemBuilds().keySet())) {
			if (v.get().building(smithy).isEmpty()) refund(server, smithy);
		}
	}

	public static Optional<Integer> of(MinecraftServer server, HeartLocation smithy) {
		return VillageClaims.current(server).map(v -> v.golemProgress(smithy)).filter(p -> p >= 0);
	}
}
