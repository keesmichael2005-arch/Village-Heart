package dev.villageheart.market;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The merchant's WORK behaviour (M22). It sets no walk target, so it runs beside {@code GoToJobBlock}; the long duration keeps vanilla's
 * {@code timedOut} from stopping it (as for the miner). Thin: it forwards each tick to {@link MerchantJob}.
 */
public final class MerchantWork extends Behavior<Villager> {
	public MerchantWork() {
		super(ImmutableMap.of(), Integer.MAX_VALUE / 2);
	}

	private static boolean isMerchant(Villager v) {
		return WorkerAttachments.get(v).map(WorkerData::job).filter(j -> j == JobType.MERCHANT).isPresent();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return isMerchant(villager) && !Workers.shouldWander(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return isMerchant(villager) && villager.getBrain().isActive(Activity.WORK) && !Workers.shouldWander(level, villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			MerchantJob.tick(level, villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			MerchantJob.stop(villager);
		}
	}
}
