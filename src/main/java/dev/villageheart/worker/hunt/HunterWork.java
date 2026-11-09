package dev.villageheart.worker.hunt;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.job.AwayTrips;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The hunter's WORK behaviour at the Lodge. Thin: it forwards each tick to {@link HunterJob}. It ends itself in the tick that hides the
 * villager (a hidden hunter is not in the brain's hands), so when the brain wakes up after the villager came back nothing stale resumes:
 * the next start plans again and sends a hunter with a yield to Storage first.
 */
public final class HunterWork extends Behavior<Villager> {
	public HunterWork() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 2);
	}

	private static boolean isHunter(Villager v) {
		return WorkerAttachments.get(v).map(WorkerData::job).filter(j -> j == JobType.HUNTER).isPresent();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return isHunter(villager) && !AwayTrips.isAway(villager); }

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return isHunter(villager) && villager.getBrain().isActive(Activity.WORK) && !AwayTrips.isAway(villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			HunterJob.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			HunterJob.stop(level, villager);
		}
	}
}
