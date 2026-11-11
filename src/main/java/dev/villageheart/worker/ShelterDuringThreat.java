package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.worker.guard.Guards;
import dev.villageheart.world.VillageClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * While a raid or a siege is tracked, a hired worker (not a guard) stands in {@link WorkerBrain#SHELTER}.
 * This runs in CORE, after vanilla's raid status, so a raid bar does not keep the worker in the raid activity.
 * Track A owns the real shelter (beds, doors, the death log). This is only the activity.
 */
public final class ShelterDuringThreat extends Behavior<Villager> {
	public ShelterDuringThreat() {
		super(ImmutableMap.of(), 1);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (Guards.isGuard(villager)) return false;
		if (villager.getBrain().isActive(WorkerBrain.SHELTER)) return false;
		return VillageClaims.current(level.getServer()).map(v -> v.threats().underAttack()).orElse(false);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().setActiveActivityIfPossible(WorkerBrain.SHELTER);
	}
}
