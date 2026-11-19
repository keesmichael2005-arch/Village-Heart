package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.worker.job.WalkTo;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Walks a worker to its bed with {@link WalkTo}. The vanilla go-home walk plans at most about 16 blocks and stops, so a
 * worker standing a workplace away from the house never arrives. A remembered bed is used, otherwise the nearest bed
 * within the same range the pathfinder can cover.
 */
public final class GoHomeWalk extends Behavior<Villager> {
	private static final int RANGE = 48;
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

	/** The remembered bed, or the nearest bed this walk can still reach. */
	private static BlockPos bed(ServerLevel level, Villager villager) {
		Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
		if (home.isPresent() && home.get().dimension().equals(level.dimension())) return home.get().pos();
		Optional<BlockPos> found = level.getPoiManager().findClosest(t -> t.is(PoiTypes.HOME), villager.blockPosition(), RANGE, PoiManager.Occupancy.ANY);
		found.ifPresent(pos -> villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), pos)));
		return found.orElse(null);
	}
}
