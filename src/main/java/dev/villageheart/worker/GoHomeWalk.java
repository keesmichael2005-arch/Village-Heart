package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.needs.Housing;
import dev.villageheart.worker.job.WalkTo;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Walks a worker to its House bed with {@link WalkTo}. The vanilla go-home walk plans at most about 16 blocks and stops,
 * and the rain stand's walk target is the workplace, so a worker left there at day time 11000 never reaches the house.
 * A barracks bed is not this bed.
 */
public final class GoHomeWalk extends Behavior<Villager> {
	private static final double REACH = 1.5;

	private final WalkTo walk = new WalkTo();

	public GoHomeWalk() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.HOME, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 4);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (Workers.tripRunning(villager) || villager.isSleeping()) return false;
		BlockPos bed = bed(level, villager);
		return bed != null && WalkTo.distance(villager, bed) > REACH;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return walk.active() && !Workers.tripRunning(villager) && !villager.isSleeping();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		BlockPos bed = bed(level, villager);
		if (bed == null) return;
		// Drop the rain stand's path to the workplace before the long walk home.
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		walk.begin(bed, REACH, gameTime);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (walk.tick(villager, gameTime) != WalkTo.State.WALKING) walk.release(villager);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		walk.release(villager);
	}

	/** The House bed, not a barracks bed that happens to be the nearest pillow. */
	private static BlockPos bed(ServerLevel level, Villager villager) {
		var beds = Housing.beds(level.getServer()).houseBeds();
		Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
		if (home.isPresent() && home.get().dimension().equals(level.dimension()) && beds.contains(home.get().pos().asLong())) {
			return home.get().pos();
		}
		BlockPos here = villager.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (long packed : beds) {
			BlockPos p = BlockPos.of(packed);
			double d = here.distSqr(p);
			if (best == null || d < bestD) {
				best = p;
				bestD = d;
			}
		}
		if (best != null) villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), best));
		return best;
	}
}
