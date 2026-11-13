package dev.villageheart.worker;

import com.google.common.collect.ImmutableMap;
import dev.villageheart.core.Balance;
import dev.villageheart.core.job.FoodKeep;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkstationStand;
import dev.villageheart.world.VillageTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * A worker carries what it holds (the villager inventory, minus what its job keeps and up to 12 food points for itself and breeding) to Storage: when the
 * load reaches {@link Balance#WORKER_CARRY_STACKS} stacks' worth (sum of count / max stack size), or when work has ended (the meeting) or the
 * walk home has started, with anything carried. It walks to a spot beside the chest that will take the goods and puts one item in every
 * {@link Balance#DEPOSIT_TICKS_PER_ITEM}. Whatever does not fit is kept. No Storage with space: status STORAGE_FULL (and the village's
 * "Storage full" alert is asked for). The walk times out after 600 ticks (STORAGE_UNREACHABLE); after any failed trip the worker waits 200
 * ticks before trying again. Trip state is not saved: after a reload the start condition is simply evaluated again.
 */
public final class DepositTrip extends Behavior<Villager> {
	private static final int WALK_TIMEOUT = 600;
	private static final int COOLDOWN = 200;
	/** Walk budget plus a full villager inventory (8 slots of 64) at the deposit pace. */
	private static final int MAX_RUN = WALK_TIMEOUT + 8 * 64 * Balance.DEPOSIT_TICKS_PER_ITEM;

	private long cooldownUntil;
	private BlockPos stand;
	private BlockPos chest;
	private long started;
	private boolean delivered;
	private boolean failed;
	private boolean arrived;
	private long nextPut;
	private final WalkTo walk = new WalkTo();

	DepositTrip() {
		super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), MAX_RUN);
	}

	/**
	 * What the worker never hands in, per item type: what its job keeps ({@link JobBehaviours#keep}: the saplings a lumberjack plants, the
	 * seeds a farmer sows) plus the food it needs for itself and for breeding ({@link FoodKeep}: up to
	 * {@link Balance#WORKER_FOOD_KEEP} food points, bread first, counting the food the job keeps).
	 */
	private static java.util.Map<net.minecraft.world.item.Item, Integer> kept(Villager v) {
		java.util.Map<net.minecraft.world.item.Item, Integer> have = new java.util.LinkedHashMap<>();
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) have.merge(s.getItem(), s.getCount(), Integer::sum);
		}
		java.util.Map<net.minecraft.world.item.Item, Integer> keep = new java.util.HashMap<>();
		java.util.Map<String, Integer> byId = new java.util.HashMap<>();
		java.util.Map<String, Integer> jobKept = new java.util.HashMap<>();
		for (var e : have.entrySet()) {
			int k = Math.min(e.getValue(), JobBehaviours.keep(v, e.getKey()));
			keep.put(e.getKey(), k);
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
			byId.put(id, e.getValue());
			jobKept.put(id, k);
		}
		for (var e : (JobBehaviours.foodKeepApplies(v) ? FoodKeep.topUp(byId, jobKept) : java.util.Map.<String, Integer>of()).entrySet()) {
			net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.getKey()));
			keep.merge(item, e.getValue(), Integer::sum);
		}
		return keep;
	}

	/** The portions to hand in: (slot, count) per inventory slot, after the kept amounts of each item type are set aside. */
	private static java.util.List<int[]> depositable(Villager v) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		java.util.Map<net.minecraft.world.item.Item, Integer> keepLeft = kept(v);
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			int left = keepLeft.getOrDefault(s.getItem(), 0);
			int keptHere = Math.min(left, s.getCount());
			keepLeft.put(s.getItem(), left - keptHere);
			if (s.getCount() - keptHere > 0) out.add(new int[] {i, s.getCount() - keptHere});
		}
		return out;
	}

	public static double load(Villager v) {
		double load = 0;
		SimpleContainer inv = v.getInventory();
		for (int[] d : depositable(v)) load += (double) d[1] / inv.getItem(d[0]).getMaxStackSize();
		return load;
	}

	private static int freeSlots(Villager v) {
		int free = 0;
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) free++;
		return free;
	}

	/**
	 * The start condition without cooldowns: something to hand in, and either a full stack's worth (Balance.WORKER_CARRY_STACKS), at most
	 * one free inventory slot, or work has ended (the meeting, or the walk home). Jobs yield to a trip while this is true (a worker that
	 * cannot deposit stops gathering, DESIGN section 6).
	 */
	public static boolean wanted(ServerLevel level, Villager villager) {
		double load = load(villager);
		if (load <= 0) return false;
		DayPhase phase = DaySchedule.phaseAt(level.getDayTime());
		boolean handIn = phase == DayPhase.MEET || phase == DayPhase.GO_HOME;
		return load >= Balance.WORKER_CARRY_STACKS || freeSlots(villager) <= 1 || handIn || JobBehaviours.depositNow(villager);
	}

	private static ItemStack firstCarried(Villager v) {
		java.util.List<int[]> d = depositable(v);
		if (d.isEmpty()) return ItemStack.EMPTY;
		ItemStack s = v.getInventory().getItem(d.get(0)[0]).copy();
		s.setCount(d.get(0)[1]);
		return s;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (Workers.tripRunning(villager)) return false; // at most one trip per worker, whichever activity started it
		if ((level.getGameTime() + villager.getId()) % 20 != 0 || level.getGameTime() < cooldownUntil) return false;
		try (var t = VillageTicker.section()) {
			if (load(villager) <= 0) {
				Workers.setTripStatus(villager.getUUID(), null);
				return false;
			}
			if (!wanted(level, villager)) return false;
			ItemStack first = firstCarried(villager);
			Optional<BlockPos> box = Stockpiles.receivingChest(level, villager.blockPosition(), first);
			if (box.isEmpty()) {
				Stockpiles.deposit(level, villager.blockPosition(), first.copy()); // stores nothing; lets the village alert notice
				Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_FULL);
				cooldownUntil = level.getGameTime() + COOLDOWN;
				return false;
			}
			BlockPos spot = WorkstationStand.spot(level, box.get(), villager.position());
			if (spot.equals(box.get())) {
				Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_UNREACHABLE);
				cooldownUntil = level.getGameTime() + COOLDOWN;
				return false;
			}
			chest = box.get();
			stand = spot;
			return true;
		}
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		started = gameTime;
		delivered = false;
		failed = false;
		arrived = false;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); // a trip walks with WalkTo; a left-over vanilla target would pull elsewhere
		Workers.setTripRunning(villager, true);
		walk.begin(stand, dev.villageheart.worker.job.WorkstationStand.REACH, gameTime);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		if (delivered || failed) return false;
		if (!arrived && gameTime - started >= WALK_TIMEOUT) return false;
		return load(villager) > 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		try (var t = VillageTicker.section()) {
			// M9: a long walk with its own path (WalkTo) instead of WALK_TARGET: the vanilla sink plans about 16 blocks and gives up on the
			// first dead end, and a lumberjack comes back from 30 blocks away, from the side of the Storage that has no door
			Workers.setTripRunning(villager, true); // refreshes the flag's clock: see Workers.tripRunning
			if (!arrived) {
				WalkTo.State st = walk.tick(villager, gameTime);
				if (st == WalkTo.State.UNREACHABLE) {
					failed = true;
					return;
				}
				if (st == WalkTo.State.WALKING) return;
				arrived = true;
				walk.release(villager);
				nextPut = gameTime + Balance.DEPOSIT_TICKS_PER_ITEM;
				return;
			}
			if (chest != null) villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chest));
			if (gameTime < nextPut) return;
			if (!putOne(level, villager)) return;
			nextPut = gameTime + Balance.DEPOSIT_TICKS_PER_ITEM;
			if (load(villager) <= 0) {
				delivered = true;
				Workers.setTripStatus(villager.getUUID(), null);
			}
		}
	}

	/** Moves one carried item into {@link #chest}. False when the trip has ended (full, or walking on to the next chest). */
	private boolean putOne(ServerLevel level, Villager villager) {
		java.util.List<int[]> slots = depositable(villager);
		if (slots.isEmpty()) {
			delivered = true;
			return false;
		}
		SimpleContainer inv = villager.getInventory();
		int slot = slots.get(0)[0];
		ItemStack stack = inv.getItem(slot);
		ItemStack moving = stack.copy();
		moving.setCount(1);
		int stored = Stockpiles.depositOne(level, chest, moving);
		if (stored == 0) {
			Optional<BlockPos> next = Stockpiles.receivingChest(level, villager.blockPosition(), stack);
			if (next.isEmpty() || next.get().equals(chest)) {
				delivered = true;
				Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_FULL);
				cooldownUntil = level.getGameTime() + COOLDOWN;
				return false;
			}
			BlockPos spot = WorkstationStand.spot(level, next.get(), villager.position());
			if (spot.equals(next.get())) {
				failed = true;
				return false;
			}
			chest = next.get();
			stand = spot;
			arrived = false;
			started = level.getGameTime();
			walk.begin(stand, dev.villageheart.worker.job.WorkstationStand.REACH, started);
			return false;
		}
		stack.shrink(1);
		inv.setChanged();
		return true;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		Workers.setTripRunning(villager, false);
		walk.release(villager);
		if (!delivered && load(villager) > 0 && (failed || (!arrived && gameTime - started >= WALK_TIMEOUT))) {
			dev.villageheart.VillageHeart.LOG.info("[trip] deposit trip failed: worker at {} could not reach the Storage chest at {} after {} ticks (failed={})",
					villager.blockPosition().toShortString(), chest == null ? "?" : chest.toShortString(), gameTime - started, failed);
			int leaves = dev.villageheart.worker.job.LeafEscape.free(level, villager);
			if (leaves > 0) dev.villageheart.VillageHeart.LOG.info("[trip] the worker was boxed in by leaves: {} broken", leaves);
			Workers.setTripStatus(villager.getUUID(), WorkerStatus.STORAGE_UNREACHABLE);
			cooldownUntil = gameTime + COOLDOWN;
		}
	}
}
