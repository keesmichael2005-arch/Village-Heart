package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.guard.Guards;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * A stroll inside the claim for a worker who has no workplace, and for an unpaid striker. It does not walk to the job block.
 * Going home, sleeping and a raid win, because those are other activities and {@link Workers#shouldWander} is false then.
 */
public final class ClaimStroll extends Behavior<Villager> {
	private static final float SPEED = 0.5F;
	private static final int REPICK = 80;

	private long nextPick;

	public ClaimStroll() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), Integer.MAX_VALUE / 4);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return wandering(level, villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return wandering(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextPick = gameTime;
		pick(level, villager, gameTime);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (gameTime < nextPick && villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) return;
		Optional<WalkTarget> target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
		if (target.isPresent() && villager.position().distanceTo(target.get().getTarget().currentPosition()) > 2.5 && gameTime < nextPick) return;
		pick(level, villager, gameTime);
	}

	private static boolean wandering(ServerLevel level, Villager villager) {
		if (!Workers.shouldWander(level, villager)) return false;
		if (Guards.isGuard(villager)) return villager.getBrain().isActive(GuardActivities.OFF);
		return villager.getBrain().isActive(Activity.WORK);
	}

	private void pick(ServerLevel level, Villager villager, long gameTime) {
		nextPick = gameTime + REPICK;
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return;
		HeartLocation heart = village.get().heart();
		BlockPos feet = villager.blockPosition();
		var rng = villager.getRandom();
		BlockPos goal = null;
		for (int i = 0; i < 8; i++) {
			int dx = 4 + rng.nextInt(8);
			int dz = 4 + rng.nextInt(8);
			if (rng.nextBoolean()) dx = -dx;
			if (rng.nextBoolean()) dz = -dz;
			BlockPos tryPos = feet.offset(dx, 0, dz);
			if (ClaimGeometry.inClaim(heart, VillageClaims.locate(level, tryPos))) {
				goal = tryPos;
				break;
			}
		}
		if (goal == null) goal = new BlockPos(heart.x(), feet.getY(), heart.z());
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goal, SPEED, 2));
	}
}
