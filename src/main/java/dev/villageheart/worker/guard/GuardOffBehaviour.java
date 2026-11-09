package dev.villageheart.worker.guard;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;

/** The guard's whole off-shift behaviour (M18 3.2 #3): hand in gear, spar at the Training Yard, or just stand about. */
final class GuardOffBehaviour extends Behavior<Villager> {
	GuardOffBehaviour() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 2);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return Guards.isGuard(villager); }

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return Guards.isGuard(villager) && villager.getBrain().isActive(GuardActivities.OFF);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		GuardRuntime.tickOff(level, villager, gameTime);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		GuardRuntime.stopOff(level, villager);
	}
}
