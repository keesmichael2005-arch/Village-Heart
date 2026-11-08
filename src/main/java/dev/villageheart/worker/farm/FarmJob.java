package dev.villageheart.worker.farm;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.farm.BoneMealTargets;
import dev.villageheart.core.farm.CompostRule;
import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.farm.FarmKeep;
import dev.villageheart.core.farm.FarmerPlanner;
import dev.villageheart.core.farm.FarmerPlanner.Action;
import dev.villageheart.core.farm.FarmerPlanner.Decision;
import dev.villageheart.core.farm.FarmerPlanner.Snapshot;
import dev.villageheart.core.farm.FieldCell;
import dev.villageheart.core.farm.FieldIndex;
import dev.villageheart.core.farm.PlantChoice;
import dev.villageheart.core.job.ToolRequirement;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.DepositTrip;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.farm.FarmRuntime.WorkerState;
import dev.villageheart.worker.job.CampContainers;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.ItemPickup;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The farmer's work, one call per tick while WORK is active (M10 plan, sections 3.2-3.5). {@link FarmerPlanner} decides; this class carries
 * it out against the world. Every unit of work is one atomic change of one cell in one server tick (a harvest and its replant, a planting,
 * a bone meal, a seed into the composter), so an interruption (fired, killed, unloaded) loses at most the unit in progress and never
 * leaves a half-handled cell. All of it runs inside {@code VillageTicker.section()}.
 */
public final class FarmJob {
	private static final String JOB = "farmer";
	static final int PLAN_INTERVAL = 20;
	private static final int ITEM_TIMEOUT = 400;
	private static final double COLLECT_TAKE = 2.2;
	private static final double CHEST_REACH = 2.0;
	private static final double COMPOSTER_REACH = 2.0;
	private static final long CELL_BLACKLIST_TICKS = 1200;
	private static final Item[] WANTED = {Items.WHEAT, Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.POISONOUS_POTATO, Items.BEETROOT,
			Items.BEETROOT_SEEDS, Items.BONE_MEAL};

	/** {@code -Dvillageheart.farm.trace=true} logs every decision (for debugging a stuck worker). */
	private static final boolean TRACE = Boolean.getBoolean("villageheart.farm.trace");

	/** Test hook: items the farmer keeps in full besides its own rules (a test fills the inventory with them). */
	public static volatile Predicate<Item> testKeepAll = i -> false;

	private static void trace(String fmt, Object... args) {
		if (TRACE) VillageHeart.LOG.info("[farm] " + fmt, args);
	}

	private FarmJob() {}

	// ---- entry points ------------------------------------------------------------------------------

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.FARMER) return;
		MinecraftServer server = level.getServer();
		FarmRuntime rt = FarmRuntime.get(server);
		WorkerState ws = rt.state(v.getUUID());
		ws.lastTick = now;
		rt.indexFor(data.jobBlock(), now);
		rt.pump(level, now);
		if (Workers.tripRunning(v)) {
			// a deposit trip owns the walk. Whichever of the two ways it started (this planner's YIELD, or the trip behaviour's own
			// start check running first), the collect target is dropped: the time away must not count against ITEM_TIMEOUT
			ws.item = null;
			ws.needsPlan = true;
			return;
		}
		if (ws.decision == null || ws.needsPlan || now - ws.lastPlan >= PLAN_INTERVAL) plan(level, v, data, rt, ws, now);
		run(level, v, data, rt, ws, now);
	}

	/** The worker was fired or died: its claims and running task are forgotten. */
	public static void forget(MinecraftServer server, UUID villager) {
		FarmRuntime rt = FarmRuntime.get(server);
		rt.claims.values().removeIf(villager::equals);
		rt.workers.remove(villager);
	}

	/** The behaviour ended (night, panic, raid, fired): the claim is released; nothing is half done. */
	public static void stop(ServerLevel level, Villager v) {
		FarmRuntime rt = FarmRuntime.get(level.getServer());
		WorkerState ws = rt.workers.get(v.getUUID());
		if (ws == null) return;
		rt.release(v.getUUID(), ws);
		ws.walk.release(v);
		ws.needsPlan = true;
		ws.item = null;
		ws.resetUnit();
		ws.action = Action.IDLE_AT_FARMHOUSE;
	}

	/** What a farmer never hands in to Storage (see {@link FarmKeep}). */
	public static int keep(Villager v, Item item) {
		if (testKeepAll.test(item)) return Integer.MAX_VALUE;
		return FarmKeep.keep(BuiltInRegistries.ITEM.getKey(item).toString(), v.getInventory().countItem(item));
	}

	// ---- helpers -----------------------------------------------------------------------------------

	private static BlockPos bp(Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

	private static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

	private static BlockPos jobBlock(WorkerData data) { return new BlockPos(data.jobBlock().x(), data.jobBlock().y(), data.jobBlock().z()); }

	private static boolean loaded(ServerLevel level, BlockPos p) { return level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null; }

	private static boolean campValid(ServerLevel level, Villager v) {
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		Optional<WorkerRecord> rec = village.get().worker(v.getUUID());
		return rec.isPresent() && Workers.status(level.getServer(), rec.get()) != WorkerStatus.WORKPLACE_INVALID;
	}

	private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }

	private static int carried(Villager v, Item item) { return v.getInventory().countItem(item); }

	private static int freeSlots(Villager v) {
		int free = 0;
		for (int i = 0; i < v.getInventory().getContainerSize(); i++) if (v.getInventory().getItem(i).isEmpty()) free++;
		return free;
	}

	private static int surplusSeeds(Villager v) {
		return FarmKeep.surplus(carried(v, Items.WHEAT_SEEDS)) + FarmKeep.surplus(carried(v, Items.BEETROOT_SEEDS));
	}

	private static double dist2(Villager v, Pos p) {
		double dx = v.getX() - (p.x() + 0.5), dy = v.getY() - p.y(), dz = v.getZ() - (p.z() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}

	private static boolean isTargeted(Action a) {
		return a == Action.HARVEST || a == Action.PLANT || a == Action.BONEMEAL || a == Action.COLLECT || a == Action.FETCH || a == Action.COMPOST;
	}

	private static double cost(Action a) {
		return switch (a) {
			case HARVEST -> Balance.FARM_TICKS_HARVEST;
			case PLANT -> Balance.FARM_TICKS_PLANT;
			case BONEMEAL -> Balance.FARM_TICKS_BONEMEAL;
			default -> Balance.FARM_TICKS_COMPOST;
		};
	}

	private static Optional<BlockState> composter(ServerLevel level, BlockPos jb) {
		if (!loaded(level, jb)) return Optional.empty();
		BlockState s = level.getBlockState(jb);
		return s.is(Blocks.COMPOSTER) ? Optional.of(s) : Optional.empty();
	}

	// ---- planning ----------------------------------------------------------------------------------

	private record Seed(CropKind kind, boolean inInventory, BlockPos chest) {}

	private static void plan(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, long now) {
		MinecraftServer server = level.getServer();
		ws.lastPlan = now;
		ws.needsPlan = false;
		boolean valid = campValid(level, v);
		boolean deposit = valid && DepositTrip.wanted(level, v);
		WorkerStatus trip = Workers.tripStatus(v.getUUID());
		boolean blocked = trip == WorkerStatus.STORAGE_FULL || trip == WorkerStatus.STORAGE_UNREACHABLE;
		FieldIndex idx = rt.indexFor(data.jobBlock(), now);
		BlockPos jb = jobBlock(data);
		Pos jbp = pos(jb);
		Optional<BlockState> comp = composter(level, jb);
		int surplus = comp.isPresent() ? surplusSeeds(v) : 0;
		boolean ready = comp.isPresent() && comp.get().getValue(ComposterBlock.LEVEL) == ComposterBlock.READY;

		List<FieldIndex.Indexed> cells = valid ? idx.cells() : List.of();
		Map<Pos, FieldCell> byPos = new HashMap<>();
		for (FieldIndex.Indexed i : cells) byPos.put(i.pos(), i.cell());
		Set<Pos> unavailable = new HashSet<>();
		for (Pos c : rt.claims.keySet()) if (rt.claimedByOther(c, v.getUUID(), now)) unavailable.add(c);
		for (var e : rt.cellBlacklist.entrySet()) if (e.getValue() > now) unavailable.add(e.getKey());

		Pos mature = null;
		double matureD = Double.MAX_VALUE;
		List<Pos> emptyCells = new ArrayList<>();
		for (FieldIndex.Indexed i : cells) {
			if (unavailable.contains(i.pos()) || !level.isPositionEntityTicking(bp(i.pos()))) continue;
			double d = dist2(v, i.pos());
			if (i.cell().isMature() && d < matureD) {
				matureD = d;
				mature = i.pos();
			} else if (i.cell().kind() == FieldCell.Kind.EMPTY_FARMLAND) {
				emptyCells.add(i.pos());
			}
		}

		// planting: which kinds can be planted (a seed carried, or in the Farmhouse chest) and what each empty cell gets
		Map<CropKind, Seed> available = new EnumMap<>(CropKind.class);
		Map<CropKind, Integer> wantedCount = new EnumMap<>(CropKind.class);
		Pos plantAt = null;
		CropKind plantKind = null;
		BlockPos plantChest = null;
		if (valid && !emptyCells.isEmpty()) {
			Optional<List<CampContainers.Chest>> chests = Optional.empty();
			boolean chestsRead = false;
			for (CropKind k : CropKind.values()) {
				Item seed = item(k.seedId());
				if (carried(v, seed) > 0) {
					available.put(k, new Seed(k, true, null));
					continue;
				}
				if (!chestsRead) {
					chests = CampContainers.of(server, data.building());
					chestsRead = true;
				}
				if (chests.isPresent()) {
					for (CampContainers.Chest ch : chests.get()) {
						if (containerCount(ch.container(), seed) > 0) {
							available.put(k, new Seed(k, false, ch.pos()));
							break;
						}
					}
				}
			}
			if (!available.isEmpty()) {
				double best = Double.MAX_VALUE;
				for (Pos c : emptyCells) {
					Optional<CropKind> choice = PlantChoice.pick(neighbours(byPos, c), available.keySet());
					if (choice.isEmpty()) continue;
					wantedCount.merge(choice.get(), 1, Integer::sum);
					double d = dist2(v, c);
					if (d < best) {
						best = d;
						plantAt = c;
						plantKind = choice.get();
					}
				}
				if (plantKind != null && !available.get(plantKind).inInventory()) plantChest = available.get(plantKind).chest();
			}
		}

		ItemEntity collect = valid ? findItem(level, v, data, rt, now) : null;
		boolean boneMeal = carried(v, Items.BONE_MEAL) > 0;
		Pos boneAt = boneMeal && valid ? BoneMealTargets.pick(filterTicking(level, cells), jbp, unavailable).orElse(null) : null;
		boolean noFarmland = idx.sweepComplete() && idx.size() == 0 && !idx.sawUnloaded();

		boolean inProgress = isTargeted(ws.action) && targetValid(level, v, rt, ws, surplus, ready, now);
		Snapshot snap = new Snapshot(valid, deposit, blocked, inProgress, freeSlots(v), surplus, mature != null, collect != null,
				plantAt != null, plantChest != null, ready, boneAt != null, idx.sweepComplete(), noFarmland);
		Decision d = FarmerPlanner.decide(snap);
		applyStatus(v.getUUID(), d.status());
		if (TRACE && (ws.decision == null || ws.decision.action() != d.action())) {
			trace("{} @{} plan {} (mature {}, plant {}->{}, collect {}, bone {}, surplus {}, ready {}, cells {})", v.getId(), now, d, mature, plantAt, plantKind,
					collect != null, boneAt, surplus, ready, cells.size());
		}
		ws.decision = d;
		if (d.action() == Action.CONTINUE) return;
		Action before = ws.action;
		ws.action = d.action();
		if (before != d.action()) ws.walk.clear();
		switch (d.action()) {
			case HARVEST -> target(rt, v, ws, mature, now);
			case PLANT -> {
				target(rt, v, ws, plantAt, now);
				ws.plantKind = plantKind;
			}
			case FETCH -> {
				rt.release(v.getUUID(), ws);
				ws.plantKind = plantKind;
				ws.fetchItem = plantKind.seedId();
				ws.fetchCount = Math.max(1, wantedCount.getOrDefault(plantKind, 1));
				ws.chest = plantChest;
				ws.walk.clear();
			}
			case BONEMEAL -> target(rt, v, ws, boneAt, now);
			case COLLECT -> {
				rt.release(v.getUUID(), ws);
				// the timeout runs from when this walk began: coming back from another action (a deposit trip can take far
				// longer than ITEM_TIMEOUT) must not count the time spent away, or the item is blacklisted on the spot
				if (before != Action.COLLECT || !collect.getUUID().equals(ws.item)) {
					ws.item = collect.getUUID();
					ws.itemSince = now;
				}
			}
			case COMPOST -> {
				rt.release(v.getUUID(), ws);
				ws.clockKey = null;
			}
			case IDLE_AT_FARMHOUSE, YIELD, CONTINUE -> {
				rt.release(v.getUUID(), ws);
				if (d.action() == Action.YIELD) ws.walk.clear();
			}
		}
	}

	private static List<FieldIndex.Indexed> filterTicking(ServerLevel level, List<FieldIndex.Indexed> cells) {
		List<FieldIndex.Indexed> out = new ArrayList<>(cells.size());
		for (FieldIndex.Indexed i : cells) if (level.isPositionEntityTicking(bp(i.pos()))) out.add(i);
		return out;
	}

	private static Map<Pos, FieldCell> neighbours(Map<Pos, FieldCell> byPos, Pos c) {
		Map<Pos, FieldCell> out = new HashMap<>();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (dx == 0 && dz == 0) continue;
			Pos p = new Pos(c.x() + dx, c.y(), c.z() + dz);
			FieldCell cell = byPos.get(p);
			if (cell != null) out.put(p, cell);
		}
		return out;
	}

	private static void target(FarmRuntime rt, Villager v, WorkerState ws, Pos cell, long now) {
		if (cell == null) {
			ws.needsPlan = true;
			return;
		}
		if (!cell.equals(ws.cell)) {
			rt.release(v.getUUID(), ws);
			ws.resetUnit();
			ws.walk.clear();
		}
		ws.cell = cell;
		rt.claims.put(cell, v.getUUID());
		ws.clock = new WorkClock(cost(ws.action));
		ws.clockKey = null;
	}

	/** True while the running task still has something to do (so the planner says CONTINUE). */
	private static boolean targetValid(ServerLevel level, Villager v, FarmRuntime rt, WorkerState ws, int surplus, boolean ready, long now) {
		return switch (ws.action) {
			case HARVEST, PLANT, BONEMEAL -> ws.cell != null && rt.claims.get(ws.cell) != null && v.getUUID().equals(rt.claims.get(ws.cell));
			case COLLECT -> ws.item != null && level.getEntity(ws.item) instanceof ItemEntity e && !e.isRemoved();
			case FETCH -> ws.chest != null && ws.fetchItem != null;
			case COMPOST -> surplus > 0 || ready;
			default -> false;
		};
	}

	private static void applyStatus(UUID id, FarmerPlanner.StatusChange change) {
		switch (change) {
			case CLEAR -> WorkerStatuses.set(id, null);
			case NO_WORK -> WorkerStatuses.set(id, WorkerStatus.NO_WORK);
			case KEEP -> { }
		}
	}

	// ---- running -----------------------------------------------------------------------------------

	private static void run(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, long now) {
		switch (ws.action) {
			case HARVEST, PLANT, BONEMEAL -> cellWork(level, v, data, rt, ws, now);
			case FETCH -> fetch(level, v, data, rt, ws, now);
			case COLLECT -> collect(level, v, data, rt, ws, now);
			case COMPOST -> compost(level, v, data, rt, ws, now);
			case IDLE_AT_FARMHOUSE -> idle(level, v, data, ws, now);
			case YIELD, CONTINUE -> { }
		}
	}

	private static void idle(ServerLevel level, Villager v, WorkerData data, WorkerState ws, long now) {
		BlockPos jb = jobBlock(data);
		if (now < ws.idleBackoffUntil || !data.jobBlock().dimension().equals(level.dimension().identifier().toString())) return;
		if (WalkTo.distance(v, jb) <= Balance.JOB_BLOCK_REACH) {
			ws.walk.release(v);
			return;
		}
		if (!ws.walk.active() || !ws.walk.target().equals(jb)) ws.walk.begin(jb, Balance.JOB_BLOCK_REACH, now);
		if (ws.walk.tick(v, now) == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.idleBackoffUntil = now + 100;
		}
	}

	/** Walks to {@code target}; true once there. A walk that gives up ends the task (returns false with {@code ws.needsPlan} set). */
	private static boolean walkTo(Villager v, WorkerState ws, BlockPos target, double reach, long now) {
		if (!ws.walk.active() || !ws.walk.target().equals(target)) ws.walk.begin(target, reach, now);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.needsPlan = true;
			return false;
		}
		return st == WalkTo.State.ARRIVED;
	}

	private static void giveUpCell(FarmRuntime rt, Villager v, WorkerState ws, Pos cell, long now) {
		rt.cellBlacklist.put(cell, now + CELL_BLACKLIST_TICKS);
		rt.giveUps.add(cell + ": " + ws.walk.reason());
		VillageHeart.LOG.info("Farmer {} cannot reach the cell {} ({}): skipped for {} ticks", v.getUUID(), cell, ws.walk.reason(), CELL_BLACKLIST_TICKS);
		rt.release(v.getUUID(), ws);
		ws.walk.clear();
		ws.resetUnit();
		ws.needsPlan = true;
	}

	// ---- one cell: harvest, plant, bone meal --------------------------------------------------------

	private static void cellWork(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, long now) {
		Pos cell = ws.cell;
		if (cell == null) {
			ws.needsPlan = true;
			return;
		}
		BlockPos bp = bp(cell);
		FieldIndex idx = rt.indexFor(data.jobBlock(), now);
		FieldCell fc = idx.reread(new LevelFieldView(level), cell);
		boolean ok = switch (ws.action) {
			case HARVEST -> fc.isMature();
			case PLANT -> fc.kind() == FieldCell.Kind.EMPTY_FARMLAND && ws.plantKind != null && carried(v, item(ws.plantKind.seedId())) > 0;
			case BONEMEAL -> fc.isYoung() && carried(v, Items.BONE_MEAL) > 0;
			default -> false;
		};
		if (!ok) {
			trace("{} drops {} at {}: {}", v.getId(), ws.action, cell, fc);
			rt.release(v.getUUID(), ws);
			ws.walk.clear();
			ws.resetUnit();
			ws.needsPlan = true;
			return;
		}
		if (!ws.walk.active() || !ws.walk.target().equals(bp)) ws.walk.begin(bp, Balance.FARM_REACH, now);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			giveUpCell(rt, v, ws, cell, now);
			return;
		}
		if (st == WalkTo.State.WALKING) return;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(bp));
		if (!cell.equals(ws.clockKey)) {
			ws.clock = new WorkClock(cost(ws.action));
			ws.clockKey = cell;
			ws.leaseTaken = false;
		}
		if (!ws.leaseTaken) {
			ws.lease = CampTools.lease(level.getServer(), v, JobType.FARMER.tool().orElseThrow().kind()) instanceof CampTools.Held h ? h : null;
			ws.leaseTaken = true;
		}
		ToolRequirement req = JobType.FARMER.tool().orElseThrow();
		double mult = WorkSpeed.multiplier(Optional.ofNullable(ws.lease).map(h -> h.lease().tier()), req, data.level(), data.needs());
		if (ws.clock.tick(mult) <= 0) return;
		switch (ws.action) {
			case HARVEST -> harvest(level, v, data, rt, ws, cell, bp, now);
			case PLANT -> plant(level, v, rt, ws, cell, bp, now);
			case BONEMEAL -> boneMeal(level, v, rt, ws, cell, bp, now);
			default -> { }
		}
		rt.release(v.getUUID(), ws);
		ws.resetUnit();
		ws.needsPlan = true;
		v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
	}

	private static void harvest(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, Pos cell, BlockPos bp, long now) {
		BlockState state = level.getBlockState(bp);
		CropKind kind = CropKind.byBlockId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()).orElse(null);
		if (kind == null) return;
		// re-read the hoe right before the swing: the player may have taken it out
		CampTools.Result r = CampTools.lease(level.getServer(), v, JobType.FARMER.tool().orElseThrow().kind());
		CampTools.Held held = r instanceof CampTools.Held h ? h : null;
		List<ItemStack> drops = new ArrayList<>();
		if (level.getGameRules().get(GameRules.BLOCK_DROPS)) {
			for (ItemStack drop : Block.getDrops(state, level, bp, null, v, held == null ? ItemStack.EMPTY : held.lease().stack())) drops.add(drop.copy());
		}
		level.destroyBlock(bp, false, v);
		// the replant seed comes out of the drops first, then the inventory; only then do the drops go to the inventory
		Item seed = item(kind.seedId());
		boolean haveSeed = false;
		for (ItemStack d : drops) {
			if (d.is(seed) && !d.isEmpty()) {
				d.shrink(1);
				haveSeed = true;
				break;
			}
		}
		if (!haveSeed && carried(v, seed) > 0) {
			v.getInventory().removeItemType(seed, 1);
			haveSeed = true;
		}
		boolean replanted = false;
		if (haveSeed) {
			BlockState fresh = BuiltInRegistries.BLOCK.getValue(Identifier.parse(kind.blockId())).defaultBlockState();
			if (fresh.canSurvive(level, bp) && level.setBlock(bp, fresh, Block.UPDATE_ALL)) {
				level.gameEvent(GameEvent.BLOCK_PLACE, bp, GameEvent.Context.of(v, fresh));
				level.playSound(null, bp, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
				replanted = true;
			} else {
				drops.add(new ItemStack(seed)); // the cell cannot hold a crop right now: keep the seed
			}
		}
		Item product = item(kind.productId());
		int gainedProduct = 0, gainedSeeds = 0;
		for (ItemStack d : drops) {
			if (d.isEmpty()) continue;
			if (d.is(product)) gainedProduct += d.getCount();
			if (d.is(Items.WHEAT_SEEDS) || d.is(Items.BEETROOT_SEEDS)) gainedSeeds += d.getCount();
			ItemStack rest = v.getInventory().addItem(d);
			if (!rest.isEmpty()) Block.popResource(level, bp, rest);
		}
		if (held != null) CampTools.use(level, held.lease(), 1);
		WorkStats.add(JOB, "harvested", 1);
		WorkStats.add(JOB, kind.productId().substring("minecraft:".length()), gainedProduct);
		WorkStats.add(JOB, "seeds", gainedSeeds);
		if (replanted) WorkStats.add(JOB, "replanted", 1);
		rt.log(now, v, "HARVEST", bp);
		trace("{} harvests {} at {} (replanted {}, hoe {})", v.getId(), kind, cell, replanted, held != null);
	}

	private static void plant(ServerLevel level, Villager v, FarmRuntime rt, WorkerState ws, Pos cell, BlockPos bp, long now) {
		CropKind kind = ws.plantKind;
		if (kind == null) return;
		Item seed = item(kind.seedId());
		BlockState fresh = BuiltInRegistries.BLOCK.getValue(Identifier.parse(kind.blockId())).defaultBlockState();
		if (carried(v, seed) <= 0 || !level.getBlockState(bp).isAir() || !fresh.canSurvive(level, bp)) return;
		if (!level.setBlock(bp, fresh, Block.UPDATE_ALL)) return;
		v.getInventory().removeItemType(seed, 1);
		level.gameEvent(GameEvent.BLOCK_PLACE, bp, GameEvent.Context.of(v, fresh));
		level.playSound(null, bp, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
		WorkStats.add(JOB, "planted", 1);
		rt.log(now, v, "PLANT", bp);
		trace("{} plants {} at {}", v.getId(), kind, cell);
	}

	private static void boneMeal(ServerLevel level, Villager v, FarmRuntime rt, WorkerState ws, Pos cell, BlockPos bp, long now) {
		if (carried(v, Items.BONE_MEAL) <= 0) return;
		ItemStack one = new ItemStack(Items.BONE_MEAL);
		if (!BoneMealItem.growCrop(one, level, bp)) return;
		v.getInventory().removeItemType(Items.BONE_MEAL, 1);
		level.levelEvent(1505, bp, 15);
		WorkStats.add(JOB, "bone_meal_used", 1);
		rt.log(now, v, "BONEMEAL", bp);
		trace("{} bone meals {}", v.getId(), cell);
	}

	// ---- seeds from the chest ----------------------------------------------------------------------

	private static int containerCount(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	private static void fetch(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, long now) {
		BlockPos chest = ws.chest;
		if (chest == null || ws.fetchItem == null) {
			ws.needsPlan = true;
			return;
		}
		if (!walkTo(v, ws, chest, CHEST_REACH, now)) return;
		Item seed = item(ws.fetchItem);
		int want = ws.fetchCount;
		Optional<List<CampContainers.Chest>> chests = CampContainers.of(level.getServer(), data.building());
		if (chests.isPresent()) {
			for (CampContainers.Chest ch : chests.get()) {
				for (int i = 0; i < ch.container().getContainerSize() && want > 0; i++) {
					ItemStack s = ch.container().getItem(i);
					if (!s.is(seed)) continue;
					int n = Math.min(want, s.getCount());
					ItemStack rest = v.getInventory().addItem(s.copyWithCount(n));
					int moved = n - rest.getCount();
					s.shrink(moved);
					want -= moved;
					ch.container().setChanged();
				}
			}
		}
		rt.log(now, v, "FETCH", chest);
		ws.walk.clear();
		ws.fetchItem = null;
		ws.chest = null;
		ws.needsPlan = true;
	}

	// ---- the composter -----------------------------------------------------------------------------

	private static void compost(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, long now) {
		BlockPos jb = jobBlock(data);
		Optional<BlockState> comp = composter(level, jb);
		if (comp.isEmpty()) {
			ws.needsPlan = true;
			return;
		}
		if (!walkTo(v, ws, jb, COMPOSTER_REACH, now)) return;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(jb));
		BlockState state = comp.get();
		int lvl = state.getValue(ComposterBlock.LEVEL);
		Pos key = pos(jb);
		if (lvl >= CompostRule.FULL) {
			if (lvl == CompostRule.FULL && !level.getBlockTicks().hasScheduledTick(jb, Blocks.COMPOSTER)) level.scheduleTick(jb, Blocks.COMPOSTER, 20);
			if (lvl == CompostRule.FULL) return; // wait for the block to turn READY
		} else if (surplusSeeds(v) <= 0) {
			ws.needsPlan = true;
			return;
		}
		if (!key.equals(ws.clockKey)) {
			ws.clock = new WorkClock(Balance.FARM_TICKS_COMPOST);
			ws.clockKey = key;
			ws.leaseTaken = false;
		}
		if (!ws.leaseTaken) {
			ws.lease = CampTools.lease(level.getServer(), v, JobType.FARMER.tool().orElseThrow().kind()) instanceof CampTools.Held h ? h : null;
			ws.leaseTaken = true;
		}
		double mult = WorkSpeed.multiplier(Optional.ofNullable(ws.lease).map(h -> h.lease().tier()), JobType.FARMER.tool().orElseThrow(), data.level(), data.needs());
		if (ws.clock.tick(mult) <= 0) return;
		ws.clock = new WorkClock(Balance.FARM_TICKS_COMPOST);
		ws.leaseTaken = false;
		ws.needsPlan = true;
		v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
		if (lvl == ComposterBlock.READY) {
			BlockState empty = state.setValue(ComposterBlock.LEVEL, 0);
			level.setBlock(jb, empty, Block.UPDATE_ALL);
			level.gameEvent(GameEvent.BLOCK_CHANGE, jb, GameEvent.Context.of(v, empty));
			level.playSound(null, jb, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			rt.tenths.remove(data.jobBlock());
			ItemStack rest = v.getInventory().addItem(new ItemStack(Items.BONE_MEAL));
			if (!rest.isEmpty()) Block.popResource(level, jb.above(), rest);
			WorkStats.add(JOB, "bone_meal_made", 1);
			rt.log(now, v, "TAKE_BONE_MEAL", jb);
			return;
		}
		Item seed = carried(v, Items.WHEAT_SEEDS) > Balance.SEED_RESERVE ? Items.WHEAT_SEEDS : Items.BEETROOT_SEEDS;
		if (carried(v, seed) <= Balance.SEED_RESERVE) return;
		CompostRule.Result res = CompostRule.insert(lvl, rt.tenths.getOrDefault(data.jobBlock(), 0));
		if (!res.accepted()) return;
		v.getInventory().removeItemType(seed, 1);
		rt.tenths.put(data.jobBlock(), res.tenths());
		BlockState next = state.setValue(ComposterBlock.LEVEL, res.level());
		level.setBlock(jb, next, Block.UPDATE_ALL);
		level.gameEvent(GameEvent.BLOCK_CHANGE, jb, GameEvent.Context.of(v, next));
		level.levelEvent(1500, jb, res.level() != lvl ? 1 : 0);
		if (res.level() == CompostRule.FULL) level.scheduleTick(jb, Blocks.COMPOSTER, 20);
		WorkStats.add(JOB, "seeds_composted", 1);
		rt.log(now, v, "COMPOST", jb);
		trace("{} composts a seed: level {} tenths {}", v.getId(), res.level(), res.tenths());
	}

	// ---- collecting --------------------------------------------------------------------------------

	private static boolean wanted(ItemStack s) {
		for (Item i : WANTED) if (s.is(i)) return true;
		return false;
	}

	/** The nearest wanted item lying in the work area (a disc of {@link Balance#FARM_RADIUS} around the composter). */
	private static ItemEntity findItem(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, long now) {
		BlockPos jb = jobBlock(data);
		double r = Balance.FARM_RADIUS;
		AABB area = new AABB(jb).inflate(r, Balance.FARM_DY + 4, r);
		ItemEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area, x -> true)) {
			if (e.isRemoved() || e.hasPickUpDelay() || !wanted(e.getItem()) || !ItemPickup.fits(v, e)) continue;
			double dx = e.getX() - (jb.getX() + 0.5), dz = e.getZ() - (jb.getZ() + 0.5);
			if (dx * dx + dz * dz > r * r) continue;
			Long until = rt.itemBlacklist.get(e.getUUID());
			if (until != null && until > now) continue;
			BlockPos ip = itemCell(e);
			BlockState at = level.getBlockState(ip);
			if (!(at.isAir() || at.canBeReplaced() || at.getCollisionShape(level, ip).isEmpty())) continue; // buried
			double d = e.distanceTo(v);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/**
	 * The block the middle of the item lies in. {@code blockPosition()} would name the farmland block for an item resting on it (the top of
	 * farmland is 1/16 below the block above), and a farmer would take such an item for buried.
	 */
	private static BlockPos itemCell(ItemEntity e) { return BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ()); }

	private static void collect(ServerLevel level, Villager v, WorkerData data, FarmRuntime rt, WorkerState ws, long now) {
		if (ws.item == null || !(level.getEntity(ws.item) instanceof ItemEntity e) || e.isRemoved()) {
			ws.item = null;
			ws.needsPlan = true;
			return;
		}
		if (now - ws.itemSince > ITEM_TIMEOUT) {
			rt.itemBlacklist.put(e.getUUID(), now + 6000);
			ws.item = null;
			ws.needsPlan = true;
			return;
		}
		double dx = e.getX() - v.getX(), dz = e.getZ() - v.getZ();
		if (Math.sqrt(dx * dx + dz * dz) <= COLLECT_TAKE && Math.abs(e.getY() - v.getY()) < 2.5) {
			ItemStack before = e.getItem().copy();
			int got = ItemPickup.collect(v, e);
			if (got > 0) rt.log(now, v, "COLLECT", itemCell(e));
			trace("{} took {} of {}", v.getId(), got, before);
			ws.item = null;
			ws.walk.clear();
			ws.needsPlan = true;
			return;
		}
		BlockPos ip = itemCell(e);
		if (!ws.walk.active()) ws.walk.begin(ip, 0.8, now);
		else ws.walk.retarget(ip);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.UNREACHABLE) {
			rt.itemBlacklist.put(e.getUUID(), now + 6000);
			ws.item = null;
			ws.walk.clear();
			ws.needsPlan = true;
		}
	}
}
