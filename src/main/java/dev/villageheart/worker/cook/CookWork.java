package dev.villageheart.worker.cook;

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
 * The cook's WORK behaviour. Thin: it runs while WORK is the active activity and forwards each tick to {@link CookJob}, which keeps its state in
 * {@link CookRuntime} (so a behaviour restart loses nothing). It idles while a deposit trip runs, so the two never fight for the walk.
 */
public final class CookWork extends Behavior<Villager> {
	public CookWork() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 2);
	}

	private static boolean isCook(Villager v) {
		return WorkerAttachments.get(v).map(WorkerData::job).filter(j -> j == JobType.COOK).isPresent();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return isCook(villager); }

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return isCook(villager) && villager.getBrain().isActive(Activity.WORK);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			CookJob.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			CookJob.stop(level, villager);
		}
	}
}
