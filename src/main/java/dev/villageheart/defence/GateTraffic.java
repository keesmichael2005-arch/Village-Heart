package dev.villageheart.defence;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/**
 * The fence-gate twin of vanilla's {@code InteractWithDoor} (human decision 2026-11-09): in the CORE of a hired worker's and a guard's
 * brain. Every 4 ticks, when the navigation path has a closed gate among the node just passed and the next three, and the walker may pass
 * it ({@link GatePaths#mayPass}: inside the claim, no hostile near unless an engaged guard), the gate is opened and handed to the
 * {@link GateKeeper}, which closes it again when nobody needs it. The path itself comes from the evaluator tweak
 * ({@code WalkNodeEvaluatorMixin}); this only opens the gate.
 */
public final class GateTraffic extends Behavior<Villager> {
	public GateTraffic() {
		super(ImmutableMap.of(), 1);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if ((level.getGameTime() + villager.getId()) % 4 != 0) return false;
		Path path = villager.getNavigation().getPath();
		return path != null && !path.isDone() && GatePaths.isHired(villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		Path path = villager.getNavigation().getPath();
		if (path == null) return;
		int from = Math.max(0, path.getNextNodeIndex() - 1), to = Math.min(path.getNodeCount() - 1, path.getNextNodeIndex() + 3);
		for (int i = from; i <= to; i++) {
			BlockPos pos = path.getNode(i).asBlockPos();
			BlockState state = level.getBlockState(pos);
			if (GatePaths.isClosedGate(state) && GatePaths.mayPass(villager, pos, state)) GateKeeper.open(level, pos, state, villager);
		}
	}
}
