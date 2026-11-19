package dev.villageheart.worker.pocket;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.job.ToolDay;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.world.VillageTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Morning, walk to the worker's chest and take the tool. At the end of work, walk back and put it in before the cargo is dumped.
 * A full chest leaves the tool in slot 0 overnight.
 */
public final class ToolErrand extends Behavior<Villager> {
	private static final double ARRIVE = 2.0;
	private final WalkTo walk = new WalkTo();
	private boolean done;

	public ToolErrand() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 600);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager v) {
		DayPhase phase = DaySchedule.phaseAt(level.getDayTime());
		if (phase == DayPhase.WAKE) return ToolDay.morning(Pockets.holdingTool(v), Pockets.chestHasTool(level.getServer(), v)) == ToolDay.Morning.TAKE;
		if (phase == DayPhase.GO_HOME) return Pockets.mustReturnTool(level, v);
		return false;
	}

	@Override
	protected void start(ServerLevel level, Villager v, long gameTime) {
		done = false;
		BlockPos chest = chest(level, v);
		if (chest == null) {
			done = true;
			return;
		}
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		walk.begin(chest, ARRIVE, gameTime);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager v, long gameTime) { return !done; }

	@Override
	protected void tick(ServerLevel level, Villager v, long gameTime) {
		try (var t = VillageTicker.section()) {
			if (walk.tick(v, gameTime) == WalkTo.State.WALKING) return;
			DayPhase phase = DaySchedule.phaseAt(level.getDayTime());
			if (phase == DayPhase.WAKE) Pockets.takeTool(level.getServer(), v);
			else Pockets.returnTool(level.getServer(), v);
			done = true;
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager v, long gameTime) { walk.release(v); }

	private static BlockPos chest(ServerLevel level, Villager v) {
		var data = v.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER);
		if (data == null || data.chest().isEmpty()) return null;
		return Pockets.pos(data.chest().get());
	}
}
