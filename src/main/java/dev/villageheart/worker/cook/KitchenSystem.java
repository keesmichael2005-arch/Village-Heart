package dev.villageheart.worker.cook;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.mixin.AbstractFurnaceBlockEntityAccessor;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every 100 ticks (M13 plan 3.2 #9 and 3.4): turns off the light of a Kitchen smoker that no cook is working at and that does not burn by itself
 * (a crash, a reload, a fired cook), and drops the heat bank of a smoker that is no longer one (its block is gone, or the village has no Kitchen
 * left). A Kitchen that is only briefly invalid keeps its banks.
 */
public final class KitchenSystem implements VillageSystem {
	public static final KitchenSystem INSTANCE = new KitchenSystem();
	private static final int INTERVAL = 100;

	private KitchenSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % INTERVAL != 0) return;
		long now = server.overworld().getGameTime();
		CookRuntime rt = CookRuntime.get(server);
		boolean anyKitchen = village.buildings().stream().anyMatch(b -> b.type().filter(t -> t == BuildingType.KITCHEN).isPresent());
		village.smokerHeat().keySet().stream().filter(l -> gone(server, l, anyKitchen)).toList().forEach(l ->
				VillageClaims.data(server).update(s -> s.withoutSmokerHeat(l::equals)));
		for (Buildings.BuildingView b : Buildings.valid(server, BuildingType.KITCHEN)) {
			if (b.latestScan().isEmpty()) continue;
			for (Pos p : b.latestScan().get().positions(BuildingRequirements.SMOKER)) {
				BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
				sweep(b.level(), pos, rt, now);
			}
		}
	}

	private static boolean gone(MinecraftServer server, HeartLocation smoker, boolean anyKitchen) {
		ServerLevel level = dev.villageheart.worker.Workers.levelOf(server, smoker);
		if (level == null) return false;
		BlockPos pos = new BlockPos(smoker.x(), smoker.y(), smoker.z());
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false; // unloaded: unknown, keep
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.SMOKER)) return !anyKitchen;
		boolean anySmithy = VillageClaims.current(server).map(v -> v.buildings().stream()
				.anyMatch(b -> b.type().filter(t -> t == dev.villageheart.core.building.BuildingType.SMITHY).isPresent())).orElse(false);
		if (state.is(Blocks.BLAST_FURNACE)) return !anySmithy;
		return true;
	}

	private static void sweep(ServerLevel level, BlockPos pos, CookRuntime rt, long now) {
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.SMOKER) || !state.getValue(AbstractFurnaceBlock.LIT)) return;
		if (rt.cooking(pos, now)) return;
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof AbstractFurnaceBlockEntityAccessor f && f.villageheart$litTimeRemaining() > 0) return; // a player's fire
		KitchenVisuals.light(level, pos, false);
	}
}
