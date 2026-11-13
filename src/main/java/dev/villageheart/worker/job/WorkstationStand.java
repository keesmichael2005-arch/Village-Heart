package dev.villageheart.worker.job;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.StandChoice;
import dev.villageheart.room.RoomScans;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * The floor cell beside a workstation or a chest. Pathing at the block itself climbs onto it; the villager stands on an
 * open side instead, same floor, one step away.
 */
public final class WorkstationStand {
	private WorkstationStand() {}

	/** True when the villager's feet are on a horizontal neighbour of {@code block}, on the same floor. On top of it is not beside it. */
	public static boolean at(Villager villager, BlockPos block) {
		BlockPos feet = villager.blockPosition();
		if (feet.getY() != block.getY()) return false;
		int dx = Math.abs(feet.getX() - block.getX());
		int dz = Math.abs(feet.getZ() - block.getZ());
		return dx + dz == 1;
	}

	/**
	 * The closest open neighbour of {@code block}. The block itself when every side is blocked, so the caller can fall
	 * back to the old reach check.
	 */
	public static BlockPos spot(ServerLevel level, BlockPos block, Vec3 from) {
		List<Pos> open = new ArrayList<>(4);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos n = block.relative(d);
			if (standable(level, n)) open.add(RoomScans.pos(n));
		}
		return StandChoice.nearest(open, RoomScans.pos(BlockPos.containing(from.x, from.y, from.z)))
				.map(RoomScans::blockPos)
				.orElse(block);
	}

	private static boolean standable(ServerLevel level, BlockPos c) {
		if (level.getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4) == null) return false;
		if (level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty()) return false;
		if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty()) return false;
		return level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty();
	}
}
