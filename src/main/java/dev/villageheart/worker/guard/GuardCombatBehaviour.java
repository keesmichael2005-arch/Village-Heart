package dev.villageheart.worker.guard;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.world.VillageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The guard's combat, in the CORE of its brain so that it runs in every activity (rest included): a wounded guard next to a zombie fights
 * back instead of walking to bed and standing there (M18 3.2 #3). Thin: {@link GuardCombat} keeps the state.
 */
final class GuardCombatBehaviour extends Behavior<Villager> {
	GuardCombatBehaviour() {
		super(ImmutableMap.of(), Integer.MAX_VALUE / 2);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return Guards.isGuard(villager); }

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) { return Guards.isGuard(villager); }

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			GuardCombat.tick(level, villager);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		GuardCombat.forget(villager.getUUID());
	}
}
