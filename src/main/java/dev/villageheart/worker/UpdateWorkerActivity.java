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
			// A walk target belongs to the activity that set it. The rain stand's is the workplace, and a
			// striker's stroll picks somewhere in the claim. Either one still set at 11000 blocks the walk
			// to the bed, and they stay a claim away from home.
			boolean leavingRain = villager.getBrain().isActive(WorkerBrain.RAIN) && want != WorkerBrain.RAIN;
			boolean goingHome = want == WorkerBrain.GO_HOME || want == Activity.REST;
			if (leavingRain || goingHome) {
				villager.getNavigation().stop();
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			villager.getBrain().setActiveActivityIfPossible(want);
		}
	}
}
