package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.needs.BedPoi;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A free bed a few blocks away is this worker's, without waiting for a path. Vanilla's acquire-bed
 * gives up when the path fails and then will not try that bed again for a while, so a worker standing
 * next to the house loses it to someone who wanders over later. The walk home still uses the path.
 */
public final class NearbyHome extends Behavior<Villager> {
	/** Far enough to cover the house next to a workplace, not the rest of the claim. */
	static final int REACH = 8;

	public NearbyHome() {
		super(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return nearest(level, villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		nearest(level, villager).ifPresent(bed -> {
			if (!BedPoi.take(level, bed)) return;
			villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		});
	}

	private static Optional<BlockPos> nearest(ServerLevel level, Villager villager) {
		BlockPos at = villager.blockPosition();
		return level.getPoiManager().findAll(
						holder -> holder.is(PoiTypes.HOME),
						pos -> BedPoi.isFree(level, pos) && pos.distSqr(at) <= (long) REACH * REACH,
						at,
						REACH,
						PoiManager.Occupancy.HAS_SPACE)
				.min(Comparator.comparingDouble(pos -> pos.distSqr(at)));
	}
}
