package dev.villageheart.worker.mine;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayTrips;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The miner's WORK behaviour on the surface. Thin: it forwards each tick to {@link MinerJob}. It ends itself in the tick that hides the
 * villager (a hidden miner is not in the brain's hands), so when the brain wakes up after surfacing nothing stale resumes: the next
 * start plans again and sends a miner with a haul to Storage first.
 */
public final class MinerWork extends Behavior<Villager> {
	public MinerWork() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 2);
	}

	private static boolean isMiner(Villager v) {
		return WorkerAttachments.get(v).map(WorkerData::job).filter(j -> j == JobType.MINER).isPresent();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return isMiner(villager) && !AwayTrips.isAway(villager) && !Workers.shouldWander(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return isMiner(villager) && villager.getBrain().isActive(Activity.WORK) && !AwayTrips.isAway(villager)
				&& !Workers.shouldWander(level, villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			MinerJob.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			MinerJob.stop(level, villager);
		}
	}
}
