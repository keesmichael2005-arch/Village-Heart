package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.world.VillageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

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
		Activity want;
		try (var t = VillageTicker.section()) {
			want = WorkerBrain.activityFor(villager, level);
		}
		if (villager.getBrain().isActive(want)) return false;
		// Rain starts and clears at once. The day clock keeps the 20-tick stagger.
		boolean rain = want == WorkerBrain.RAIN || villager.getBrain().isActive(WorkerBrain.RAIN);
		if (!rain && (level.getGameTime() + villager.getId()) % 20 != 0) return false;
		return true;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			Activity want = WorkerBrain.activityFor(villager, level);
			// The rain stand's walk target is the workplace. Leaving it has to drop that target,
			// or home time keeps them a workplace away from the bed.
			if (villager.getBrain().isActive(WorkerBrain.RAIN) && want != WorkerBrain.RAIN) {
				villager.getNavigation().stop();
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			villager.getBrain().setActiveActivityIfPossible(want);
		}
	}
}
