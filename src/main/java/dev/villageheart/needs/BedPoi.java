package dev.villageheart.needs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Bed checks shared by the re-homing (M11) and the baby's bed (M23): is the block a bed nobody lies in, and does its HOME POI have a free ticket. */
public final class BedPoi {
	private BedPoi() {}

	/** The block is a bed and nobody is lying in it. */
	public static boolean isFree(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.is(BlockTags.BEDS) && !s.getValue(BedBlock.OCCUPIED);
	}

	/** The HOME POI at {@code p} has a free ticket. */
	public static boolean hasSpace(PoiManager poi, BlockPos p) {
		return poi.getInSquare(t -> t.is(PoiTypes.HOME), p, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(p));
	}

	/** The block is a bed and has a HOME POI (taken or not). */
	public static boolean isBedWithPoi(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).is(BlockTags.BEDS) && level.getPoiManager().exists(p, t -> t.is(PoiTypes.HOME));
	}

	/** A bed head that is loaded, free, has a HOME POI and a free ticket (plan 3.2 #3). */
	public static boolean isFreeBed(ServerLevel level, BlockPos p) {
		return level.isLoaded(p) && isFree(level, p) && level.getPoiManager().exists(p, t -> t.is(PoiTypes.HOME)) && hasSpace(level.getPoiManager(), p);
	}

	/** Takes the ticket of the HOME POI at {@code p} (the same call vanilla's make-love and the re-homing use). */
	public static boolean take(ServerLevel level, BlockPos p) {
		return level.getPoiManager().take(t -> t.is(PoiTypes.HOME), (t, q) -> q.equals(p), p, 1).isPresent();
	}

	/** Gives a taken ticket back; true when the POI exists. */
	public static void release(ServerLevel level, BlockPos p) {
		PoiManager poi = level.getPoiManager();
		if (poi.exists(p, t -> t.is(PoiTypes.HOME))) {
			poi.release(p);
			level.debugSynchronizers().updatePoi(p);
		}
	}
}
