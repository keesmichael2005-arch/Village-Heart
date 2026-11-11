package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.world.VillageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Keeps the worker's active activity equal to {@link WorkerBrain#activityFor}. Present in IDLE, WORK, go_home, REST and SHELTER
 * (not in PANIC, raid or hide, which must not be interrupted). Checks every 20 ticks (spread by entity id). Also corrects the drop to IDLE
 * that vanilla's calm-down / raid-end code causes by asking a schedule-less brain for its schedule activity.
 */
public final class UpdateWorkerActivity extends Behavior<Villager> {
	public UpdateWorkerActivity() {
		super(ImmutableMap.of(), 1);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if ((level.getGameTime() + villager.getId()) % 20 != 0) return false;
		try (var t = VillageTicker.section()) {
			return !villager.getBrain().isActive(WorkerBrain.activityFor(villager, level));
		}
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			villager.getBrain().setActiveActivityIfPossible(WorkerBrain.activityFor(villager, level));
		}
	}
}
