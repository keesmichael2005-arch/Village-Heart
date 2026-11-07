package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * While WORK is active: when the worker has no walk target and is farther than {@link Balance#JOB_BLOCK_REACH} from its job block, walk
 * there. Backs off for 100 ticks after the pathfinder reports the block unreachable (no re-path every tick), and does nothing when the job
 * block is in another dimension. Within reach it stamps {@code LAST_WORKED_AT_POI} (vanilla golem gossip reads it).
 */
final class GoToJobBlock extends Behavior<Villager> {
	private static final int UNREACHABLE_BACKOFF_TICKS = 100;

	GoToJobBlock() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
				MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
				MemoryModuleType.LAST_WORKED_AT_POI, MemoryStatus.REGISTERED), 20);
	}

	/** The worker's job block when it is in this level and loaded data says so, else empty. */
	static Optional<BlockPos> jobBlockIn(ServerLevel level, Villager villager) {
		WorkerData data = villager.getAttached(WorkerAttachments.WORKER);
		if (data == null) return Optional.empty();
		HeartLocation b = data.jobBlock();
		if (!b.dimension().equals(level.dimension().identifier().toString())) return Optional.empty();
		return Optional.of(new BlockPos(b.x(), b.y(), b.z()));
	}

	static double distanceTo(Villager villager, BlockPos block) {
		return villager.position().distanceTo(Vec3.atBottomCenterOf(block));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		try (var t = VillageTicker.section()) {
			Optional<BlockPos> block = jobBlockIn(level, villager);
			if (block.isEmpty()) return false;
			if (distanceTo(villager, block.get()) <= Balance.JOB_BLOCK_REACH) return false;
			Optional<Long> since = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
			return since.isEmpty() || level.getGameTime() - since.get() >= UNREACHABLE_BACKOFF_TICKS;
		}
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			jobBlockIn(level, villager).ifPresent(block -> {
				villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(block, 0.5F, 1));
			});
		}
	}
}
