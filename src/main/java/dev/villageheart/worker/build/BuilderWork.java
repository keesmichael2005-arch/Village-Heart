package dev.villageheart.worker.build;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The builder's WORK behaviour. Thin: it runs while WORK is the active activity and forwards each tick to {@link BuilderJob}, which keeps its
 * own state in the build runtime and the build records in the saved village data.
 */
public final class BuilderWork extends Behavior<Villager> {
	public BuilderWork() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 2);
	}

	private static boolean isBuilder(Villager v) {
		return WorkerAttachments.get(v).map(WorkerData::job).filter(j -> j == JobType.BUILDER).isPresent();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return isBuilder(villager) && !Workers.shouldWander(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return isBuilder(villager) && villager.getBrain().isActive(Activity.WORK) && !Workers.shouldWander(level, villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			if (WallRepair.tick(level, villager, gameTime)) return;
			BuilderJob.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			WallRepair.stop(villager);
			BuilderJob.stop(level, villager);
		}
	}
}
