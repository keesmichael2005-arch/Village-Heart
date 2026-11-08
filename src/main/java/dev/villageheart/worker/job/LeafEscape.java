package dev.villageheart.worker.job;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A worker boxed in by leaves (it cut into a canopy, or stands where one grew) cannot path anywhere. When a walk has just failed as unreachable,
 * the leaves touching the worker (its feet and head level, and the cell above) are broken, so the next plan has a way out. Only leaves: a worker
 * walled in by anything else is left alone.
 */
public final class LeafEscape {
	private LeafEscape() {}

	/** @return how many leaf blocks were broken */
	public static int free(ServerLevel level, Villager v) {
		BlockPos base = v.blockPosition();
		int broken = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
			BlockPos p = base.offset(dx, dy, dz);
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.LEAVES) && level.destroyBlock(p, true, v)) broken++;
		}
		return broken;
	}
}
