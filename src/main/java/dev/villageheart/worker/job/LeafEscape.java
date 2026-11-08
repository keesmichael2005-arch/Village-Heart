package dev.villageheart.worker.job;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * A worker boxed in by leaves (it cut into a canopy, or stands where one grew) cannot path anywhere. When a walk has just failed as unreachable
 * and the worker really is enclosed, the natural leaves touching it (its feet and head level, and the cell above) are broken, so the next plan
 * has a way out.
 * <ul>
 * <li>Enclosed: all four horizontal neighbours at feet and head level block movement, and at least one of them is natural leaves. A worker that
 * merely failed a trip next to a hedge has a free side, so nothing is touched.</li>
 * <li>Persistent leaves (what a player places, which never decay) are never broken.</li>
 * <li>Like vanilla villager farming, nothing is broken when the {@code mobGriefing} gamerule is off.</li>
 * </ul>
 */
public final class LeafEscape {
	private LeafEscape() {}

	/** @return how many leaf blocks were broken */
	public static int free(ServerLevel level, Villager v) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) return 0;
		BlockPos base = v.blockPosition();
		if (!enclosed(level, base)) return 0;
		int broken = 0;
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
			BlockPos p = base.offset(dx, dy, dz);
			if (natural(level.getBlockState(p)) && level.destroyBlock(p, true, v)) broken++;
		}
		return broken;
	}

	/** Natural (decaying) leaves: not placed by a player. */
	static boolean natural(BlockState s) {
		return s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT));
	}

	/** Every horizontal neighbour at feet and head level blocks movement, and at least one is natural leaves. */
	static boolean enclosed(ServerLevel level, BlockPos base) {
		boolean leaves = false;
		for (Direction d : Direction.Plane.HORIZONTAL) for (int dy = 0; dy <= 1; dy++) {
			BlockPos p = base.relative(d).above(dy);
			BlockState s = level.getBlockState(p);
			if (s.getCollisionShape(level, p).isEmpty()) return false;
			if (natural(s)) leaves = true;
		}
		return leaves;
	}
}
