package dev.villageheart.worker.smith;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.mixin.AbstractFurnaceBlockEntityAccessor;
import dev.villageheart.worker.cook.KitchenVisuals;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Refunds a build whose Smithy is gone, and turns off a blast furnace no smith is using. */
public final class SmithySystem implements VillageSystem {
	public static final SmithySystem INSTANCE = new SmithySystem();
	private static final int INTERVAL = 100;

	private SmithySystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % INTERVAL != 0) return;
		GolemBuilds.refundUnregistered(server);
		SmithCrafts.refundOrphans(server);
		SmithCrafts.retryPending(server);
		long now = server.overworld().getGameTime();
		SmithRuntime rt = SmithRuntime.get(server);
		for (Buildings.BuildingView b : Buildings.valid(server, BuildingType.SMITHY)) {
			if (b.latestScan().isEmpty()) continue;
			for (Pos p : b.latestScan().get().positions(BuildingRequirements.BLAST_FURNACE)) {
				sweep(b.level(), new BlockPos(p.x(), p.y(), p.z()), rt, now);
			}
		}
	}

	private static void sweep(ServerLevel level, BlockPos pos, SmithRuntime rt, long now) {
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return;
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.BLAST_FURNACE) || !state.getValue(AbstractFurnaceBlock.LIT)) return;
		if (rt.working(pos, now)) return;
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof AbstractFurnaceBlockEntityAccessor f && f.villageheart$litTimeRemaining() > 0) return;
		KitchenVisuals.light(level, pos, false);
	}

	/** The furnace a heat entry names, when it is still a blast furnace of a Smithy. */
	public static boolean blastOfSmithy(MinecraftServer server, HeartLocation loc) {
		ServerLevel level = dev.villageheart.worker.Workers.levelOf(server, loc);
		if (level == null) return false;
		BlockPos pos = new BlockPos(loc.x(), loc.y(), loc.z());
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return true;
		return level.getBlockState(pos).is(Blocks.BLAST_FURNACE) && SmithyLayout.smithyAt(server, loc).isPresent();
	}
}
