package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkstationStand;
import dev.villageheart.world.VillageTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The meeting: walk to a spot beside the bell and stay there. A deposit trip owns the walk while it runs, and a villager
 * who is already asleep is not in this activity.
 */
public final class MeetAtBell extends Behavior<Villager> {
	private final WalkTo walk = new WalkTo();

	MeetAtBell() {
		super(ImmutableMap.of(), 20 * 60 * 10);
	}

	private static Optional<BlockPos> bell(ServerLevel level, Villager villager) {
		Optional<GlobalPos> mem = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
		if (mem.isEmpty() || !mem.get().dimension().equals(level.dimension())) return Optional.empty();
		return Optional.of(mem.get().pos());
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (Workers.tripRunning(villager) || villager.isSleeping()) return false;
		Optional<BlockPos> bell = bell(level, villager);
		return bell.isPresent() && !WorkstationStand.at(villager, bell.get());
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			bell(level, villager).ifPresent(b -> {
				BlockPos spot = WorkstationStand.spot(level, b, villager.position());
				walk.begin(spot, spot.equals(b) ? 1.5 : 1.0, gameTime);
			});
		}
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		if (Workers.tripRunning(villager) || villager.isSleeping()) return false;
		Optional<BlockPos> bell = bell(level, villager);
		return bell.isPresent() && !WorkstationStand.at(villager, bell.get());
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			if (Workers.tripRunning(villager)) {
				walk.release(villager);
				return;
			}
			Optional<BlockPos> bell = bell(level, villager);
			if (bell.isEmpty()) return;
			BlockPos spot = WorkstationStand.spot(level, bell.get(), villager.position());
			if (!walk.active() || !walk.target().equals(spot)) walk.begin(spot, spot.equals(bell.get()) ? 1.5 : 1.0, gameTime);
			walk.tick(villager, gameTime);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		walk.release(villager);
	}
}
