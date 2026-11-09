package dev.villageheart.worker.lumber;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.WorkerAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The lumberjack's WORK behaviour. Thin: it runs while WORK is the active activity and forwards each tick to {@link LumberJob}, which
 * keeps its own state in {@link LumberRuntime} (so a behaviour restart loses nothing) and the tree tasks in the saved village data.
 */
public final class LumberjackWork extends Behavior<Villager> {
	public LumberjackWork() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 2);
	}

	private static boolean isLumberjack(Villager v) {
		return WorkerAttachments.get(v).map(WorkerData::job).filter(j -> j == JobType.LUMBERJACK).isPresent();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return isLumberjack(villager); }

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return isLumberjack(villager) && villager.getBrain().isActive(Activity.WORK);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			LumberJob.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			LumberJob.stop(level, villager);
		}
	}
}
