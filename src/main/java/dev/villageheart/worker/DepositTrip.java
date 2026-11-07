package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.Balance;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A worker carries what it holds (the villager inventory, minus the food vanilla villagers keep for themselves) to Storage: when the
 * load reaches {@link Balance#WORKER_CARRY_STACKS} stacks' worth (sum of count / max stack size), or when the day's work is over (go_home)
 * with anything carried. It walks to the door of the nearest Storage with space and hands the stacks to that Storage; whatever does not fit
 * is kept. No Storage with space: status STORAGE_FULL (and the village's "Storage full" alert is asked for). A trip times out after 600
 * ticks (STORAGE_UNREACHABLE); after any failed trip the worker waits 200 ticks before trying again. Trip state is not saved: after a
 * reload the start condition is simply evaluated again.
 */
final class DepositTrip extends Behavior<Villager> {
	private static final int TIMEOUT = 600;
	private static final int COOLDOWN = 200;
	private static final double ARRIVE = 2.0;

	private long cooldownUntil;
	private BlockPos target;
	private long started;
	private boolean delivered;

	DepositTrip() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), TIMEOUT);
	}

	/** Items a worker never hands in: the food it needs for itself and for breeding. */
	private static boolean keeps(ItemStack s) { return Villager.FOOD_POINTS.containsKey(s.getItem()); }

	static double load(Villager v) {
		double load = 0;
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && !keeps(s)) load += (double) s.getCount() / s.getMaxStackSize();
		}
		return load;
	}

	private static ItemStack firstCarried(Villager v) {
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && !keeps(s)) return s;
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if ((level.getGameTime() + villager.getId()) % 20 != 0 || level.getGameTime() < cooldownUntil) return false;
		try (var t = VillageTicker.section()) {
			double load = load(villager);
			if (load <= 0) {
				Workers.setTripStatus(villager.getUUID(), null);
				return false;
			}
			boolean homeTime = DaySchedule.phaseAt(level.getDayTime()) == DayPhase.GO_HOME;
			if (load < Balance.WORKER_CARRY_STACKS && !homeTime) return false;
			ItemStack first = firstCarried(villager);
			Optional<BlockPos> storage = Stockpiles.nearestWithSpace(level, villager.blockPosition(), first);
			if (storage.isEmpty()) {
				Stockpiles.deposit(level, villager.blockPosition(), first.copy()); // stores nothing; lets the village alert notice
				Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_FULL);
				cooldownUntil = level.getGameTime() + COOLDOWN;
				return false;
			}
			target = storage.get();
			return true;
		}
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		started = gameTime;
		delivered = false;
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 1));
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !delivered && gameTime - started < TIMEOUT && load(villager) > 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			if (villager.position().distanceTo(Vec3.atBottomCenterOf(target)) > ARRIVE) {
				if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty() && (gameTime + villager.getId()) % 20 == 0) {
					villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 1));
				}
				return;
			}
			SimpleContainer inv = villager.getInventory();
			boolean left = false;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.isEmpty() || keeps(s)) continue;
				Stockpiles.depositHere(level, target, s);
				if (!s.isEmpty()) left = true;
			}
			inv.setChanged();
			delivered = true;
			if (left) {
				Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_FULL);
				cooldownUntil = gameTime + COOLDOWN;
			} else {
				Workers.setTripStatus(villager.getUUID(), null);
			}
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		if (!delivered && load(villager) > 0 && gameTime - started >= TIMEOUT) {
			Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_UNREACHABLE);
			cooldownUntil = gameTime + COOLDOWN;
		}
	}
}
