package dev.villageheart.worker.job;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * A worker whose body is inside a solid block (a sapling it stood in grew into a trunk, a player built around it) cannot path anywhere: every
 * trip and walk fails. Like vanilla's push-out for a suffocating player, it is moved to the nearest cell where it fits and has ground below,
 * within {@link #RADIUS} blocks. Called for every worker by the worker system.
 */
public final class WorkerUnstick {
	private WorkerUnstick() {}

	public static final int RADIUS = 3;

	/** @return true when the worker was inside a block and was moved (or could not be, and stays) */
	public static boolean rescue(ServerLevel level, Villager v) {
		if (AwayTrips.isAway(v)) return false; // a hidden miner or hunter belongs to AwayTrips (it pins it to its hold cell)
		if (level.noBlockCollision(v, v.getBoundingBox())) return false;
		BlockPos base = v.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) for (int dy = -1; dy <= 3; dy++) {
			BlockPos p = base.offset(dx, dy, dz);
			double d = dx * dx + dz * dz + dy * dy;
			if (d >= bestD) continue;
			AABB box = v.getBoundingBox().move(p.getX() + 0.5 - v.getX(), p.getY() - v.getY(), p.getZ() + 0.5 - v.getZ());
			if (!level.noBlockCollision(v, box)) continue;
			if (level.noBlockCollision(v, box.move(0, -0.5, 0))) continue; // nothing to stand on
			best = p;
			bestD = d;
		}
		if (best == null) return true;
		v.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
		v.getNavigation().stop();
		dev.villageheart.VillageHeart.LOG.info("[unstick] worker {} was inside a block at {}: moved to {}", v.getUUID(), base.toShortString(), best.toShortString());
		return true;
	}
}
