package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.needs.HomeBed;
import dev.villageheart.core.room.Pos;
import dev.villageheart.needs.Housing;
import dev.villageheart.worker.job.WalkTo;
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
 * Walks a worker to its bed with {@link WalkTo}. The vanilla go-home walk plans at most about 16 blocks and stops,
 * and the rain stand's walk target is the workplace, so a worker left there at day time 11000 never reaches the bed.
 * The bed they already have is kept, including one that is not in a House. A barracks bed is not that bed when a House
 * bed is in reach. Lying down is {@code SleepInBed}, which runs only at sleep time.
 */
public final class GoHomeWalk extends Behavior<Villager> {
	private static final int RANGE = 48;
	private static final double REACH = 1.5;

	private final WalkTo walk = new WalkTo();

	public GoHomeWalk() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.HOME, MemoryStatus.REGISTERED),
				Integer.MAX_VALUE / 4);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (Workers.tripRunning(villager) || villager.isSleeping()) return false;
		BlockPos bed = bed(level, villager);
		return bed != null && WalkTo.distance(villager, bed) > REACH;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return walk.active() && !Workers.tripRunning(villager) && !villager.isSleeping();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		BlockPos bed = bed(level, villager);
		if (bed == null) return;
		// Drop the rain stand's path to the workplace before the long walk home.
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		walk.begin(bed, REACH, gameTime);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (walk.tick(villager, gameTime) != WalkTo.State.WALKING) walk.release(villager);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		walk.release(villager);
	}

	/** The bed they already have, or the nearest House bed when that memory is a barracks bed. */
	private static BlockPos bed(ServerLevel level, Villager villager) {
		var index = Housing.beds(level.getServer());
		Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
		boolean here = home.isPresent() && home.get().dimension().equals(level.dimension());
		Optional<Long> remembered = here ? Optional.of(home.get().pos().asLong()) : Optional.empty();
		boolean otherBuilding = remembered.isPresent() && index.otherBuildingBed(remembered.get());
		Pos at = new Pos(villager.getBlockX(), villager.getBlockY(), villager.getBlockZ());
		Optional<Long> chosen = HomeBed.choose(remembered, otherBuilding, index.houseBeds(), at, RANGE);
		if (chosen.isPresent()) {
			BlockPos pos = BlockPos.of(chosen.get());
			if (remembered.isEmpty() || remembered.get() != chosen.get()) {
				villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), pos));
			}
			return pos;
		}
		Optional<BlockPos> found = level.getPoiManager().findClosest(t -> t.is(PoiTypes.HOME), villager.blockPosition(), RANGE, PoiManager.Occupancy.ANY);
		found.ifPresent(pos -> villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), pos)));
		return found.orElse(null);
	}
}
