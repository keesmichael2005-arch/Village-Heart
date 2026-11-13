package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.Balance;
import dev.villageheart.world.VillageTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;
import java.util.Optional;

/** Within reach of its job block a worker counts as having worked there: stamps {@code LAST_WORKED_AT_POI} every 20 ticks. */
final class StandAtJobBlock extends Behavior<Villager> {
	StandAtJobBlock() {
		super(ImmutableMap.of(MemoryModuleType.LAST_WORKED_AT_POI, MemoryStatus.REGISTERED), 1);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (Workers.shouldWander(level, villager)) return false;
		if ((level.getGameTime() + villager.getId()) % 20 != 0) return false;
		try (var t = VillageTicker.section()) {
			Optional<BlockPos> block = GoToJobBlock.jobBlockIn(level, villager);
			return block.isPresent() && GoToJobBlock.distanceTo(villager, block.get()) <= Balance.JOB_BLOCK_REACH;
		}
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, gameTime);
	}
}
