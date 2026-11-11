package dev.villageheart.worker.smith;

import dev.villageheart.core.Balance;
import dev.villageheart.core.gear.CampDemand;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.cook.FuelTable;
import dev.villageheart.core.gear.CancelCheck;
import dev.villageheart.core.gear.CraftPlan;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearDemand;
import dev.villageheart.core.gear.GearDemandPlanner;
import dev.villageheart.core.gear.GearOrder;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SlotPresence;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.smith.GolemRules;
import dev.villageheart.core.smith.RepairRules;
import dev.villageheart.core.smith.SmithBatch;
import dev.villageheart.core.smith.SmithCraft;
import dev.villageheart.core.smith.SmithDecision;
import dev.villageheart.core.smith.SmithFacts;
import dev.villageheart.core.smith.SmithKeep;
import dev.villageheart.core.smith.SmithKind;
import dev.villageheart.core.smith.SmithPlanner;
import dev.villageheart.core.smith.SmithRecipe;
import dev.villageheart.core.smith.SmithRecipes;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.golem.SmithyRepairs;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.DepositTrip;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.cook.KitchenVisuals;
import dev.villageheart.worker.gear.GearOrders;
import dev.villageheart.worker.gear.ToolCensus;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** The blacksmith's day. The planner decides; this class walks, fetches, smelts, crafts and builds. */
public final class SmithJob {
	private static final int PLAN_INTERVAL = 20;
	private static final int VALID_INTERVAL = 100;
	private static final double REACH = 2.0;
	private static final double CLOSEST = 1.0;
	private static final String JOB = "blacksmith";
	private static long lastSurplus = Long.MIN_VALUE;

	private SmithJob() {}

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.BLACKSMITH) return;
		SmithRuntime rt = SmithRuntime.get(level.getServer());
		SmithRuntime.State st = rt.state(v.getUUID());
		if (Workers.tripRunning(v)) return;
		if (now - st.lastValid >= VALID_INTERVAL) {
			st.lastValid = now;
			st.valid = ToolCensus.valid(level.getServer(), data.building());
			if (!st.valid) abort(level, v, st, true);
		}
		if (!st.valid) {
			idleAt(level, v, st, SmithyLayout.block(data.jobBlock()), now);
			return;
		}
		if (st.mode != SmithRuntime.Mode.IDLE) {
			run(level, v, data, st, now);
			return;
		}
		if (now < st.retryAt) {
			idleAt(level, v, st, SmithyLayout.block(data.jobBlock()), now);
			return;
		}
		if (st.needsPlan || now - st.lastPlan >= PLAN_INTERVAL) plan(level, v, data, st, now);
		if (st.mode == SmithRuntime.Mode.IDLE) idleAt(level, v, st, SmithyLayout.block(data.jobBlock()), now);
		else run(level, v, data, st, now);
	}

	public static void stop(ServerLevel level, Villager v) {
		SmithRuntime.State st = SmithRuntime.get(level.getServer()).workers.get(v.getUUID());
		if (st == null) return;
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data != null && (st.mode == SmithRuntime.Mode.CRAFT || st.mode == SmithRuntime.Mode.PREREQ)) saveCraft(level.getServer(), data, st);
		if (data != null && st.mode == SmithRuntime.Mode.BUILD) saveBuild(level.getServer(), data, st);
		if (st.mode == SmithRuntime.Mode.FETCH || st.mode == SmithRuntime.Mode.FETCH_REPAIR) {
			Stockpiles.reservations(level.getServer()).release(v.getUUID());
			st.wanted.clear();
			if (st.carriedTool == null) st.keep.clear();
		}
		if (st.mode == SmithRuntime.Mode.SMELT) {
			st.batch = null;
			st.keep.clear();
		}
		unlight(level, st);
		st.walk.release(v);
		if (st.mode != SmithRuntime.Mode.DELIVER) st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
	}

	public static void forget(MinecraftServer server, UUID smith) {
		SmithRuntime rt = SmithRuntime.get(server);
		SmithRuntime.State st = rt.workers.remove(smith);
		Stockpiles.reservations(server).release(smith);
		GearOrders.releaseHolder(smith);
		rt.buildOwner.entrySet().removeIf(e -> e.getValue().equals(smith));
		if (st != null && st.litAt != null) {
			for (ServerLevel l : server.getAllLevels()) KitchenVisuals.light(l, st.litAt, false);
		}
	}

	public static int keep(Villager v, net.minecraft.world.item.Item item) {
		if (!(v.level() instanceof ServerLevel level)) return 0;
		SmithRuntime.State st = SmithRuntime.get(level.getServer()).state(v.getUUID());
		boolean home = DaySchedule.phaseAt(level.getDayTime()) != DayPhase.WORK;
		String id = SmithCatalog.id(item);
		return SmithKeep.keep(id, count(v, id), st.keep, st.returning, home, st.overnight.contains(id));
	}

	/** Anything that is not a kept job item is handed in at once. */
	public static boolean depositNow(Villager v) { return true; }

	// ---- planning ----------------------------------------------------------------------------------

	private static void plan(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		MinecraftServer server = level.getServer();
		st.lastPlan = now;
		st.needsPlan = false;
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		if (now != lastSurplus) {
			lastSurplus = now;
			cancelSurplus(server, village.get());
			village = VillageClaims.current(server);
			if (village.isEmpty()) return;
		}
		adopt(server, v, data, village.get());
		village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		SmithFacts f = facts(level, v, data, st, village.get(), now);
		HeartLocation furnace = data.jobBlock();
		Optional<SmithCraft> saved = village.get().smithCraft(furnace);
		if (saved.isPresent() && GearOrders.of(saved.get().craft().key()).isEmpty())
			GearOrders.claim(saved.get().craft().key(), v.getUUID(), now, true);
		SmithDecision d = SmithPlanner.decide(f);
		apply(level, v, data, st, d, now);
	}

	private static void apply(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, SmithDecision d, long now) {
		st.returning = false;
		st.blockedHint = "";
		switch (d) {
			case SmithDecision.Yield y -> {
			}
			case SmithDecision.Idle i -> {
				st.hint = i.hint();
				st.material = i.material();
				st.returning = i.returning();
				status(v, i.hint());
			}
			case SmithDecision.Smelt s -> {
				st.hint = SmithDecision.Hint.OK;
				startSmelt(level, v, data, st, s.batch(), now);
			}
			case SmithDecision.StartCraft c -> {
				st.hint = SmithDecision.Hint.OK;
				startCraft(level, v, data, st, c.order(), now);
			}
			case SmithDecision.FetchAndDeliver f -> {
				st.hint = SmithDecision.Hint.OK;
				startDeliverFetch(level, v, data, st, f.key(), f.itemId(), now);
			}
			case SmithDecision.Craft c -> {
				st.cancelCount = c.consecutiveDefinite();
				st.hint = SmithDecision.Hint.OK;
				resumeCraft(level, v, data, st);
			}
			case SmithDecision.CancelCraft c -> cancelSaved(level, v, data, st);
			case SmithDecision.Deliver del -> {
				st.hint = SmithDecision.Hint.OK;
				st.mode = SmithRuntime.Mode.DELIVER;
				st.carriedTool = del.key();
			}
			case SmithDecision.BuildGolem b -> {
				st.hint = SmithDecision.Hint.OK;
				resumeBuild(level, v, data, st, now);
			}
			case SmithDecision.StartGolem s -> {
				st.hint = SmithDecision.Hint.OK;
				startBuild(level, v, data, st, now);
			}
			case SmithDecision.RepairGolem r -> {
				st.hint = SmithDecision.Hint.OK;
				beginGolemRepair(level, v, data, st, r.ingots(), now);
			}
			case SmithDecision.RepairInPlace r -> {
				st.hint = SmithDecision.Hint.OK;
				RepairScan.Hit hit = match(RepairScan.inPlace(level.getServer(), data.building()), r.job());
				if (hit == null) {
					st.needsPlan = true;
					return;
				}
				st.repairHit = hit;
				st.repairAcc = 0;
				st.mode = SmithRuntime.Mode.REPAIR_SLOT;
			}
			case SmithDecision.FetchForRepair r -> {
				st.hint = SmithDecision.Hint.OK;
				st.repairQueue = RepairScan.fetchable(level.getServer(), VillageClaims.current(level.getServer()).orElseThrow());
				st.repairQueueIndex = 0;
				st.mode = SmithRuntime.Mode.FETCH_REPAIR;
			}
			default -> {
			}
		}
		if (!(d instanceof SmithDecision.Idle)) WorkerStatuses.set(v.getUUID(), null);
	}

	private static void status(Villager v, SmithDecision.Hint hint) {
		WorkerStatus s = switch (hint) {
			case NEEDS_FUEL -> WorkerStatus.NEEDS_FUEL;
			case NO_WORK, NEEDS_MATERIAL, NEEDS_VILLAGERS -> WorkerStatus.NO_WORK;
			default -> null;
		};
		WorkerStatuses.set(v.getUUID(), s);
	}

	private static SmithFacts facts(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, VillageState village, long now) {
		MinecraftServer server = level.getServer();
		SmithFacts f = SmithFacts.morning();
		DemandCensus.View view = DemandCensus.of(server, village);
		f.smithyValid = st.valid;
		f.speed = WorkSpeed.untooled(data.level(), data.needs());
		f.depositNow = DepositTrip.load(v) > 0;
		WorkerStatus trip = Workers.tripStatus(v.getUUID());
		f.trip = trip == WorkerStatus.STORAGE_FULL ? SmithFacts.Trip.FULL
				: trip == WorkerStatus.STORAGE_UNREACHABLE ? SmithFacts.Trip.UNREACHABLE : SmithFacts.Trip.NONE;
		f.dayTime = Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS);
		f.demands = view.demands();
		f.openByCamp = view.open();
		f.storageTools = view.storageTools();
		f.materials = view.materials();
		f.stock = carriedStock(v, view.stock());
		f.fuelCount = new HashMap<>(view.fuelCount());
		f.fuelHeat = view.fuelHeat();
		for (var e : carried(v).entrySet()) if (SmithCatalog.heatOrZero(e.getKey()) > 0) f.fuelCount.merge(e.getKey(), e.getValue(), Integer::sum);
		f.furnaceBank = village.smokerHeat(data.jobBlock());
		f.cookHired = view.cookHired();
		f.smeltableUnits = (SmithBatch.availableHeat(f.fuelCount, f.fuelHeat, f.cookHired) + Math.max(0, f.furnaceBank)) / FuelTable.HEAT_PER_ITEM;
		f.gear = view.gear();
		f.freeSlots = freeSlots(v);
		BlockPos furnace = SmithyLayout.block(data.jobBlock());
		int dist = Stockpiles.nearestHolding(level, furnace, k -> true).map(p -> (int) Math.ceil(Math.sqrt(furnace.distSqr(p)))).orElse(8);
		f.walkTicks = 3 * dist * Balance.COOK_WALK_TICKS_PER_BLOCK + Balance.COOK_WALK_SLACK;
		f.currentBatch = Optional.ofNullable(st.batch);
		expireBlocks(st, now);
		f.deliveryBlocked = new HashSet<>(st.blockedUntil.keySet());
		if (st.carriedTool != null) f.carriedTool = Optional.of(st.carriedTool);
		Optional<SmithCraft> saved = village.smithCraft(data.jobBlock());
		f.savedCraft = saved.map(c -> c.craft().key());
		if (saved.isPresent()) {
			f.craftPresence = presence(server, village, saved.get().craft().key());
			f.craftCancelCount = st.cancelCount;
		}
		golemFacts(level, v, data, st, village, f);
		List<RepairScan.Hit> place = RepairScan.inPlace(server, data.building());
		for (RepairScan.Hit h : place) if (RepairScan.affordable(h, f.stock)) {
			f.chestRepair = h.job();
			break;
		}
		List<SmithFacts.RepairJob> fetch = new ArrayList<>();
		for (RepairScan.Hit h : RepairScan.fetchable(server, village)) if (RepairScan.affordable(h, f.stock)) fetch.add(h.job());
		f.fetchRepairs = fetch;
		return f;
	}

	private static void golemFacts(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, VillageState village, SmithFacts f) {
		HeartLocation smithy = data.building();
		int progress = village.golemProgress(smithy);
		f.buildSaved = progress >= 0;
		UUID owner = SmithRuntime.get(level.getServer()).buildOwner.get(smithy);
		boolean ownerAlive = owner != null && village.worker(owner).isPresent();
		f.buildOwnedByMe = !f.buildSaved || owner == null || !ownerAlive || owner.equals(v.getUUID());
		List<net.minecraft.world.entity.npc.villager.Villager> census = VillageCensus.of(level.getServer(), village);
		Set<UUID> ids = new HashSet<>();
		for (Villager c : census) ids.add(c.getUUID());
		int pop = GolemRules.population(ids, village.workers());
		int golems = golemsInClaim(level, village);
		f.golemAllowed = GolemRules.allowed(pop, golems, village.golemBuilds().size());
		SmithyRepairs.waitingAt(smithy).ifPresent(id -> {
			if (!(level.getEntity(id) instanceof IronGolem g) || !g.isAlive()) {
				SmithyRepairs.release(id);
				return;
			}
			int n = GolemRules.ingotsFor(g.getHealth(), g.getMaxHealth());
			f.golemWaiting = java.util.OptionalInt.of(n);
			long iron = f.stock.getOrDefault("minecraft:iron_ingot", 0L);
			f.golemIngotsReady = n > 0 && iron >= n;
		});
	}

	private static Map<String, Long> carriedStock(Villager v, Map<String, Long> stock) {
		Map<String, Long> out = new HashMap<>(stock);
		carried(v).forEach((id, n) -> out.merge(id, n.longValue(), Long::sum));
		return out;
	}

	private static Map<String, Integer> carried(Villager v) {
		Map<String, Integer> out = new HashMap<>();
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !s.getComponentsPatch().isEmpty()) continue;
			out.merge(SmithCatalog.id(s.getItem()), s.getCount(), Integer::sum);
		}
		return out;
	}

	// ---- modes -------------------------------------------------------------------------------------

	private static void run(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		if (preempt(level, v, data, st, now)) return;
		switch (st.mode) {
			case FETCH -> fetchTick(level, v, data, st, now);
			case SMELT -> smeltTick(level, v, data, st, now);
			case PREREQ -> prereqTick(level, v, data, st, now);
			case CRAFT -> craftTick(level, v, data, st, now);
			case DELIVER -> deliverTick(level, v, data, st, now);
			case BUILD -> buildTick(level, v, data, st, now);
			case REPAIR_GOLEM -> repairGolemTick(level, v, data, st, now);
			case REPAIR_SLOT -> repairSlotTick(level, v, data, st, now);
			case FETCH_REPAIR -> fetchRepairTick(level, v, data, st, now);
			default -> {
			}
		}
	}

	/** A hurt golem interrupts a smelt, a craft or a repair. The interrupted job resumes afterwards. */
	private static boolean preempt(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		if (st.mode != SmithRuntime.Mode.SMELT && st.mode != SmithRuntime.Mode.CRAFT && st.mode != SmithRuntime.Mode.PREREQ
				&& st.mode != SmithRuntime.Mode.REPAIR_SLOT) return false;
		if ((now + v.getId()) % Balance.SMITH_CANCEL_CHECK_TICKS != 0) return false;
		var waiting = SmithyRepairs.waitingAt(data.building());
		if (waiting.isEmpty() || !(level.getEntity(waiting.get()) instanceof IronGolem g)) return false;
		int n = GolemRules.ingotsFor(g.getHealth(), g.getMaxHealth());
		if (n < 1 || count(v, "minecraft:iron_ingot") + stockCount(level, "minecraft:iron_ingot") < n) return false;
		st.paused = st.mode;
		beginGolemRepair(level, v, data, st, n, now);
		return true;
	}

	private static void idleAt(ServerLevel level, Villager v, SmithRuntime.State st, BlockPos furnace, long now) {
		if (!st.walk.active() || !furnace.equals(st.walk.target())) st.walk.begin(furnace, REACH, CLOSEST, now);
		WalkTo.State ws = st.walk.tick(v, now);
		if (ws == WalkTo.State.UNREACHABLE) st.walk.clear();
		if (WalkTo.distance(v, furnace) <= REACH) v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
	}

	// ---- smelt -------------------------------------------------------------------------------------

	private static void startSmelt(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, SmithBatch.Step step, long now) {
		SmithRecipe primary = SmithRecipes.byOutput(step.outputId()).filter(r -> r.kind() == SmithKind.SMELT).orElse(null);
		if (primary == null) {
			st.needsPlan = true;
			return;
		}
		// One walk smelts the pile (up to a stack), not only the planner's batch of 8. The planner still decides that
		// smelting is the job; the smith checks for a new demand again once the pile is done.
		double speed = Math.max(0.01, WorkSpeed.untooled(data.level(), data.needs()));
		long day = Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS);
		int room = (int) Math.floor((Balance.SMITH_DONE_BY - day - 200) * speed / Balance.SMITH_SMELT_TICKS);
		room = Math.min(64, Math.max(step.units(), room));
		List<String> raws = new ArrayList<>();
		raws.add(primary.inputs().get(0).itemId());
		for (SmithRecipe r : SmithRecipes.ALL) {
			if (r.kind() != SmithKind.SMELT) continue;
			String id = r.inputs().get(0).itemId();
			if (!raws.contains(id)) raws.add(id);
		}
		Map<String, Integer> want = new HashMap<>();
		int total = 0;
		for (String raw : raws) {
			if (total >= room) break;
			int have = count(v, raw);
			int avail = have + (int) Math.min(room, Stockpiles.available(level.getServer(), StackKey.of(SmithCatalog.item(raw)), v.getUUID()));
			int take = Math.min(avail, room - total);
			if (take > have) want.put(raw, take - have);
			total += Math.max(0, take);
		}
		if (total < 1) {
			st.needsPlan = true;
			return;
		}
		int bank = Math.max(0, bank(level.getServer(), data.jobBlock()));
		int need = Math.max(0, total * FuelTable.HEAT_PER_ITEM - bank);
		if (need > 0) addFuel(level, v, want, need);
		for (var e : new ArrayList<>(want.entrySet())) {
			int have = count(v, e.getKey());
			if (have >= e.getValue()) want.remove(e.getKey());
			else want.put(e.getKey(), e.getValue() - have);
		}
		st.batch = new SmithBatch.Step(step.outputId(), total, step.fuelId(), step.fuelItems());
		st.batchLeft = total;
		if (want.isEmpty()) {
			st.mode = SmithRuntime.Mode.SMELT;
			for (String raw : raws) if (count(v, raw) > 0) st.keep.add(raw);
			for (var e : carried(v).entrySet()) if (SmithCatalog.heatOrZero(e.getKey()) > 0) st.keep.add(e.getKey());
			return;
		}
		st.order = null;
		st.fetchKey = null;
		beginFetch(level, v, data, st, want, null, now);
	}

	private static void smeltTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		BlockPos furnace = SmithyLayout.block(data.jobBlock());
		if (!at(level, v, st, furnace, now)) return;
		String raw = nextRaw(v, st.batch);
		SmithRecipe recipe = raw == null ? null : recipeForRaw(raw);
		if (recipe == null || st.batchLeft < 1) {
			finishSmelt(level, v, st);
			return;
		}
		int bank = bank(level.getServer(), data.jobBlock());
		if (bank < FuelTable.HEAT_PER_ITEM) {
			bank = loadFuel(level.getServer(), v, data, bank);
			if (bank < FuelTable.HEAT_PER_ITEM) {
				finishSmelt(level, v, st);
				return;
			}
		}
		light(level, v, st, furnace, now);
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		st.prereqAcc += speed;
		if (st.prereqAcc < Balance.SMITH_SMELT_TICKS) return;
		st.prereqAcc -= Balance.SMITH_SMELT_TICKS;
		if (remove(v, raw, 1) < 1) return;
		setBank(level.getServer(), data.jobBlock(), bank(level.getServer(), data.jobBlock()) - FuelTable.HEAT_PER_ITEM);
		give(v, recipe.outputId(), 1);
		st.keep.add(recipe.outputId());
		v.swing(InteractionHand.MAIN_HAND);
		WorkStats.add(JOB, "items", 1);
		WorkXp.award(v, WorkUnit.ITEM_SMITHED, 1);
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, "SMELT", recipe.outputId()));
		st.batchLeft--;
		if (st.batchLeft < 1) finishSmelt(level, v, st);
	}

	/** The ore still in hand. The batch's own ore goes first; any other raw ore the trip picked up follows. */
	private static String nextRaw(Villager v, SmithBatch.Step batch) {
		if (batch != null) {
			SmithRecipe primary = SmithRecipes.byOutput(batch.outputId()).filter(r -> r.kind() == SmithKind.SMELT).orElse(null);
			if (primary != null && count(v, primary.inputs().get(0).itemId()) > 0) return primary.inputs().get(0).itemId();
		}
		for (SmithRecipe r : SmithRecipes.ALL) {
			if (r.kind() != SmithKind.SMELT) continue;
			if (count(v, r.inputs().get(0).itemId()) > 0) return r.inputs().get(0).itemId();
		}
		return null;
	}

	private static SmithRecipe recipeForRaw(String raw) {
		for (SmithRecipe r : SmithRecipes.ALL) {
			if (r.kind() == SmithKind.SMELT && r.inputs().get(0).itemId().equals(raw)) return r;
		}
		return null;
	}

	private static void finishSmelt(ServerLevel level, Villager v, SmithRuntime.State st) {
		st.batch = null;
		st.keep.clear();
		st.prereqAcc = 0;
		unlight(level, st);
		st.walk.release(v);
		st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
	}

	// ---- craft -------------------------------------------------------------------------------------

	private static void startCraft(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, GearOrder order, long now) {
		st.order = order;
		st.batch = null;
		st.fetchKey = order.key();
		Map<String, Integer> want = new HashMap<>();
		for (ItemCount c : order.plan().take()) want.merge(c.id(), c.count(), Integer::sum);
		int smelts = 0;
		for (SmithRecipe p : order.prerequisites()) if (p.kind() == SmithKind.SMELT) smelts++;
		if (smelts > 0) {
			int bank = bank(level.getServer(), data.jobBlock());
			int need = smelts * FuelTable.HEAT_PER_ITEM - Math.min(bank, smelts * FuelTable.HEAT_PER_ITEM);
			if (need > 0) addFuel(level, v, want, need);
		}
		for (var e : new ArrayList<>(want.entrySet())) {
			int have = count(v, e.getKey());
			if (have >= e.getValue()) want.remove(e.getKey());
			else want.put(e.getKey(), e.getValue() - have);
		}
		GearOrders.claim(order.key(), v.getUUID(), now, false);
		if (want.isEmpty()) {
			commitCraft(level, v, data, st, now);
			return;
		}
		beginFetch(level, v, data, st, want, order.key(), now);
	}

	private static void startDeliverFetch(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, OrderKey key, String itemId, long now) {
		st.order = null;
		st.fetchKey = key;
		st.fetchToolId = itemId;
		GearOrders.claim(key, v.getUUID(), now, true);
		if (count(v, itemId) >= 1) {
			st.carriedTool = key;
			st.keep.add(itemId);
			st.overnight.add(itemId);
			st.mode = SmithRuntime.Mode.DELIVER;
			return;
		}
		beginFetch(level, v, data, st, Map.of(itemId, 1), key, now);
	}

	private static void beginFetch(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, Map<String, Integer> want, OrderKey key, long now) {
		MinecraftServer server = level.getServer();
		var res = Stockpiles.reservations(server);
		res.release(v.getUUID());
		st.wanted.clear();
		for (var e : want.entrySet()) {
			StackKey stack = StackKey.of(SmithCatalog.item(e.getKey()));
			int n = (int) Math.min(e.getValue(), Stockpiles.available(server, stack, v.getUUID()));
			if (n > 0) {
				res.set(v.getUUID(), stack, n);
				st.wanted.put(e.getKey(), n);
				st.keep.add(e.getKey());
			}
		}
		if (st.wanted.isEmpty()) {
			st.retryAt = now + 100;
			st.needsPlan = true;
			st.mode = SmithRuntime.Mode.IDLE;
			if (key != null && st.order != null) GearOrders.release(key);
			return;
		}
		BlockPos furnace = SmithyLayout.block(data.jobBlock());
		BlockPos anchor = Stockpiles.nearestHolding(level, furnace, k -> st.wanted.containsKey(k.id())).orElse(furnace);
		st.fetchAnchor = anchor;
		st.fetchStart = now;
		st.mode = SmithRuntime.Mode.FETCH;
		st.walk.begin(anchor, REACH, CLOSEST, now);
	}

	private static void fetchTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		if (st.fetchKey != null && due(st, now) && cancelIf(level, v, data, st, now)) return;
		BlockPos furnace = SmithyLayout.block(data.jobBlock());
		if (st.fetchAnchor != null) {
			WalkTo.State ws = st.walk.tick(v, now);
			if (ws == WalkTo.State.UNREACHABLE || now - st.fetchStart > Balance.SMITH_ORPHAN_TICKS) {
				abortFetch(level, v, st, now);
				return;
			}
			if (ws != WalkTo.State.ARRIVED) return;
			withdraw(level, v, st, st.fetchAnchor);
			Stockpiles.reservations(level.getServer()).release(v.getUUID());
			st.fetchAnchor = null;
			st.walk.begin(furnace, REACH, CLOSEST, now);
			return;
		}
		if (!at(level, v, st, furnace, now)) return;
		if (st.fetchToolId != null) {
			if (count(v, st.fetchToolId) < 1) {
				abortFetch(level, v, st, now);
				return;
			}
			st.carriedTool = st.fetchKey;
			st.overnight.add(st.fetchToolId);
			st.mode = SmithRuntime.Mode.DELIVER;
			st.walk.release(v);
			return;
		}
		if (st.buildFetch) {
			st.buildFetch = false;
			if (count(v, "minecraft:iron_ingot") < Balance.GOLEM_INGOTS || count(v, "minecraft:carved_pumpkin") < 1) {
				abortFetch(level, v, st, now);
				return;
			}
			remove(v, "minecraft:iron_ingot", Balance.GOLEM_INGOTS);
			remove(v, "minecraft:carved_pumpkin", 1);
			st.keep.remove("minecraft:iron_ingot");
			st.keep.remove("minecraft:carved_pumpkin");
			GolemBuilds.start(level.getServer(), data.building());
			SmithRuntime.get(level.getServer()).buildOwner.put(data.building(), v.getUUID());
			st.golemAcc = 0;
			st.buildStart = now;
			st.mode = SmithRuntime.Mode.BUILD;
			st.walk.release(v);
			WorkStats.add(JOB, "golems_started", 1);
			return;
		}
		if (st.repairGolem != null && st.order == null) {
			st.keep.add("minecraft:iron_ingot");
			st.mode = SmithRuntime.Mode.REPAIR_GOLEM;
			st.lastHeal = now - Balance.GOLEM_REPAIR_TICKS;
			st.walk.release(v);
			return;
		}
		if (st.batch != null && st.order == null) {
			st.mode = SmithRuntime.Mode.SMELT;
			st.walk.release(v);
			return;
		}
		if (st.order == null || !SmithPlanner.readyToCommit(st.order.plan(), carried(v))) {
			abortFetch(level, v, st, now);
			return;
		}
		commitCraft(level, v, data, st, now);
	}

	private static void withdraw(ServerLevel level, Villager v, SmithRuntime.State st, BlockPos anchor) {
		BlockPos at = anchor == null ? v.blockPosition() : anchor;
		for (var e : st.wanted.entrySet()) {
			StackKey key = StackKey.of(SmithCatalog.item(e.getKey()));
			int left = e.getValue();
			while (left > 0) {
				ItemStack s = Stockpiles.withdrawAt(level, at, key, left, v.getUUID());
				if (s.isEmpty()) break;
				left -= s.getCount();
				ItemStack rest = v.getInventory().addItem(s);
				if (!rest.isEmpty()) Stockpiles.deposit(level, at, rest);
			}
		}
		st.wanted.clear();
	}

	private static void commitCraft(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		GearOrder order = st.order;
		if (order == null) return;
		List<ItemCount> committed = new ArrayList<>();
		List<ItemCount> taken = new ArrayList<>();
		for (ItemCount ic : order.plan().take()) {
			int n = remove(v, ic.id(), ic.count());
			taken.add(new ItemCount(ic.id(), n));
			if (n < ic.count()) {
				for (ItemCount back : taken) give(v, back.id(), back.count());
				abortFetch(level, v, st, now);
				return;
			}
			committed.add(new ItemCount(ic.id(), n));
		}
		st.keep.clear();
		for (SmithRecipe p : order.prerequisites()) if (p.kind() == SmithKind.SMELT) {
			// fuel stays in hand for the smelts
		}
		carried(v).keySet().stream().filter(SmithJob::isFuelId).forEach(st.keep::add);
		HeartLocation dest = order.demand().destination();
		SmithCrafts.commit(level.getServer(), data.jobBlock(), new SmithCraft(new GearCraft(order.recipe().id(), order.key(), 0, committed), dest));
		st.progressAcc = 0;
		st.lastSavedProgress = 0;
		st.prereqIndex = 0;
		st.prereqAcc = 0;
		st.cancelCount = 0;
		st.lastCancelCheck = now;
		st.mode = order.prerequisites().isEmpty() ? SmithRuntime.Mode.CRAFT : SmithRuntime.Mode.PREREQ;
		st.walk.release(v);
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, "COMMIT", order.recipe().id()));
	}

	private static void resumeCraft(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st) {
		Optional<SmithCraft> saved = VillageClaims.current(level.getServer()).flatMap(vs -> vs.smithCraft(data.jobBlock()));
		if (saved.isEmpty()) {
			st.needsPlan = true;
			return;
		}
		SmithRecipe recipe = SmithRecipes.byOutput(saved.get().craft().recipeId()).orElse(null);
		if (recipe == null) {
			SmithCrafts.refund(level.getServer(), data.jobBlock(), saved.get());
			st.needsPlan = true;
			return;
		}
		if (st.order == null || !st.order.key().equals(saved.get().craft().key())) st.order = synthetic(saved.get(), recipe);
		st.progressAcc = Math.max(st.progressAcc, saved.get().craft().progress());
		st.mode = st.prereqIndex < st.order.prerequisites().size() && st.progressAcc <= 0 && !st.order.prerequisites().isEmpty()
				? SmithRuntime.Mode.PREREQ : SmithRuntime.Mode.CRAFT;
		if (saved.get().craft().progress() > 0) st.mode = SmithRuntime.Mode.CRAFT;
	}

	private static GearOrder synthetic(SmithCraft saved, SmithRecipe recipe) {
		CraftPlan plan = new CraftPlan(recipe.shape().orElseThrow(), saved.craft().committed(), 0, 0, List.of());
		GearDemand demand = new GearDemand(saved.craft().key(), "", dev.villageheart.core.gear.Urgency.U1_MISSING_REQUIRED,
				dev.villageheart.core.gear.SlotState.MISSING, saved.destination(), java.util.OptionalInt.empty(), false);
		return new GearOrder(demand, recipe, plan, List.of());
	}

	private static void prereqTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		if (st.order == null || st.prereqIndex >= st.order.prerequisites().size()) {
			st.mode = SmithRuntime.Mode.CRAFT;
			return;
		}
		SmithRecipe step = st.order.prerequisites().get(st.prereqIndex);
		BlockPos at = step.kind() == SmithKind.SMELT ? SmithyLayout.block(data.jobBlock())
				: SmithyLayout.anvil(level.getServer(), data.building(), SmithyLayout.block(data.jobBlock())).orElse(SmithyLayout.block(data.jobBlock()));
		if (!at(level, v, st, at, now)) return;
		if (step.kind() == SmithKind.SMELT) light(level, v, st, SmithyLayout.block(data.jobBlock()), now);
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		st.prereqAcc += speed;
		if (st.prereqAcc < step.ticksPerUnit()) return;
		st.prereqAcc = 0;
		if (!applyPrereq(level, v, data, st, step, now)) {
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		st.prereqIndex++;
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, step.kind().name(), step.outputId()));
		if (st.prereqIndex >= st.order.prerequisites().size()) {
			unlight(level, st);
			st.mode = SmithRuntime.Mode.CRAFT;
		}
	}

	private static boolean applyPrereq(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, SmithRecipe step, long now) {
		MinecraftServer server = level.getServer();
		Optional<SmithCraft> saved = VillageClaims.current(server).flatMap(vs -> vs.smithCraft(data.jobBlock()));
		if (saved.isEmpty()) return false;
		List<ItemCount> items = new ArrayList<>(saved.get().craft().committed());
		if (step.kind() == SmithKind.SMELT) {
			String raw = step.inputs().get(0).itemId();
			if (!take(items, raw, 1)) return false;
			add(items, step.outputId(), 1);
			int bank = bank(server, data.jobBlock());
			if (bank < FuelTable.HEAT_PER_ITEM) bank = loadFuel(server, v, data, bank);
			if (bank < FuelTable.HEAT_PER_ITEM) return false;
			setBank(server, data.jobBlock(), bank(server, data.jobBlock()) - FuelTable.HEAT_PER_ITEM);
			WorkXp.award(v, WorkUnit.ITEM_SMITHED, 1);
		} else if (step.kind() == SmithKind.STICKS) {
			if (!takePlanks(items, 2)) return false;
			add(items, "minecraft:stick", 4);
		}
		for (ItemCount left : st.order.plan().leftovers()) {
			int n = takeCount(items, left.id());
			int back = Math.min(n, left.count());
			if (back > 0) {
				take(items, left.id(), back);
				give(v, left.id(), back);
				st.keep.remove(left.id());
			}
		}
		HeartLocation furnace = data.jobBlock();
		SmithCraft next = new SmithCraft(new GearCraft(saved.get().craft().recipeId(), saved.get().craft().key(), 0, items), saved.get().destination());
		VillageClaims.data(server).update(vs -> vs.smithCraft(furnace).isPresent() ? vs.withSmithCraft(furnace, next) : vs);
		return true;
	}

	private static void craftTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		Optional<SmithCraft> saved = VillageClaims.current(level.getServer()).flatMap(vs -> vs.smithCraft(data.jobBlock()));
		if (saved.isEmpty() || st.order == null) {
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		if (due(st, now) && cancelIf(level, v, data, st, now)) return;
		BlockPos anvil = SmithyLayout.anvil(level.getServer(), data.building(), SmithyLayout.block(data.jobBlock()))
				.orElse(SmithyLayout.block(data.jobBlock()));
		if (!at(level, v, st, anvil, now)) return;
		if (presence(level.getServer(), VillageClaims.current(level.getServer()).orElseThrow(), saved.get().craft().key()) == SlotPresence.UNKNOWN) return;
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		if (speed <= 0) return;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(anvil));
		v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
		st.progressAcc += speed;
		int whole = (int) st.progressAcc;
		if (st.lastSavedProgress < 0 || whole - st.lastSavedProgress >= Balance.SMITH_CRAFT_SAVE_TICKS) {
			SmithCrafts.progress(level.getServer(), data.jobBlock(), saved.get().craft().recipeId(), saved.get().craft().key(), whole);
			st.lastSavedProgress = whole;
		}
		int need = st.order.recipe().ticksPerUnit();
		if (st.progressAcc + 1e-6 < need) return;
		if (!SmithCrafts.finish(level.getServer(), data.jobBlock(), saved.get().craft().recipeId(), saved.get().craft().key())) return;
		give(v, st.order.recipe().outputId(), st.order.recipe().outputCount());
		int xp = Math.max(1, need / Balance.SMITH_XP_TICKS);
		WorkXp.award(v, WorkUnit.ITEM_SMITHED, xp);
		WorkStats.add(JOB, "crafts", 1);
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, "CRAFT", st.order.recipe().outputId()));
		st.carriedTool = st.order.key();
		st.keep.add(st.order.recipe().outputId());
		st.overnight.add(st.order.recipe().outputId());
		st.progressAcc = 0;
		st.mode = SmithRuntime.Mode.DELIVER;
		st.walk.release(v);
	}

	private static void deliverTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		OrderKey key = st.carriedTool;
		if (key == null) {
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		if (st.blockedUntil.getOrDefault(key, 0L) > now) {
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		String itemId = st.order != null ? st.order.recipe().outputId() : st.fetchToolId;
		if (itemId == null || count(v, itemId) < 1) {
			st.carriedTool = null;
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		BlockPos chest = firstChest(level.getServer(), key.building());
		if (chest == null) {
			st.retryAt = now + 40;
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		if (!at(level, v, st, chest, now)) return;
		ItemStack stack = takeStack(v, itemId);
		if (stack.isEmpty()) return;
		if (!insert(level, chest, stack)) {
			Stockpiles.deposit(level, v.blockPosition(), stack);
			if (!stack.isEmpty()) v.getInventory().addItem(stack);
			st.blockedUntil.put(key, now + Balance.SMITH_DELIVERY_BACKOFF);
			st.blockedHint = buildingName(level.getServer(), key.building()) + " chest is full";
			st.hint = SmithDecision.Hint.NO_WORK;
			st.keep.remove(itemId);
			st.overnight.remove(itemId);
			st.carriedTool = null;
			GearOrders.release(key);
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			st.walk.release(v);
			return;
		}
		tidyWorn(level, data, key);
		GearOrders.release(key);
		st.keep.remove(itemId);
		st.overnight.remove(itemId);
		st.carriedTool = null;
		st.fetchToolId = null;
		st.order = null;
		st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
		st.walk.release(v);
		WorkStats.add(JOB, "delivered", 1);
	}

	// ---- golems ------------------------------------------------------------------------------------

	private static void startBuild(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		int iron = count(v, "minecraft:iron_ingot");
		int pumpkin = count(v, "minecraft:carved_pumpkin");
		if (iron >= Balance.GOLEM_INGOTS && pumpkin >= 1) {
			remove(v, "minecraft:iron_ingot", Balance.GOLEM_INGOTS);
			remove(v, "minecraft:carved_pumpkin", 1);
			GolemBuilds.start(level.getServer(), data.building());
			SmithRuntime.get(level.getServer()).buildOwner.put(data.building(), v.getUUID());
			st.golemAcc = 0;
			st.buildStart = now;
			st.mode = SmithRuntime.Mode.BUILD;
			WorkStats.add(JOB, "golems_started", 1);
			return;
		}
		Map<String, Integer> want = new HashMap<>();
		if (iron < Balance.GOLEM_INGOTS) want.put("minecraft:iron_ingot", Balance.GOLEM_INGOTS - iron);
		if (pumpkin < 1) want.put("minecraft:carved_pumpkin", 1);
		st.buildFetch = true;
		st.order = null;
		st.fetchKey = null;
		beginFetch(level, v, data, st, want, null, now);
		if (st.mode != SmithRuntime.Mode.FETCH) st.buildFetch = false;
	}

	private static void resumeBuild(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		int p = VillageClaims.current(level.getServer()).map(vs -> vs.golemProgress(data.building())).orElse(-1);
		if (p < 0) {
			st.needsPlan = true;
			return;
		}
		SmithRuntime.get(level.getServer()).buildOwner.putIfAbsent(data.building(), v.getUUID());
		st.golemAcc = p;
		if (st.buildStart < 0) st.buildStart = now;
		st.mode = SmithRuntime.Mode.BUILD;
	}

	private static void buildTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		BlockPos anvil = SmithyLayout.anvil(level.getServer(), data.building(), SmithyLayout.block(data.jobBlock()))
				.orElse(SmithyLayout.block(data.jobBlock()));
		if (!at(level, v, st, anvil, now)) return;
		light(level, v, st, SmithyLayout.block(data.jobBlock()), now);
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		if (speed <= 0) return;
		if ((int) st.golemAcc % Balance.GOLEM_HAMMER_TICKS == 0) {
			v.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.4f, 1f);
		}
		st.golemAcc += speed;
		int whole = (int) Math.min(st.golemAcc, Balance.GOLEM_BUILD_TICKS);
		if (whole % Balance.SMITH_CRAFT_SAVE_TICKS == 0) GolemBuilds.progress(level.getServer(), data.building(), whole);
		if (st.golemAcc < Balance.GOLEM_BUILD_TICKS) return;
		if ((now + v.getId()) % 100 != 0 && st.waitSpot == null) {
			// try every tick the first time, then every 100 if the spot was missing
		}
		var scan = SmithyLayout.scan(level.getServer(), data.building());
		if (scan.isEmpty()) return;
		Optional<BlockPos> spot = SmithyLayout.waitSpot(level, scan.get());
		if (spot.isEmpty()) return;
		st.waitSpot = spot.get();
		var spawned = SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, EntitySpawnReason.MOB_SUMMONED, level, spot.get(), 10, 2, 3,
				SpawnUtil.Strategy.LEGACY_IRON_GOLEM, true);
		if (spawned.isEmpty() || !(spawned.get() instanceof IronGolem g)) return;
		g.setPlayerCreated(true);
		level.playSound(null, g.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1f, 1f);
		if (!GolemBuilds.finish(level.getServer(), data.building())) {
			g.discard();
			return;
		}
		WorkXp.award(v, WorkUnit.ITEM_SMITHED, 20);
		WorkStats.add(JOB, "golems_built", 1);
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, "GOLEM", "minecraft:iron_golem"));
		SmithRuntime.get(level.getServer()).buildOwner.remove(data.building());
		unlight(level, st);
		st.golemAcc = 0;
		st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
		st.walk.release(v);
	}

	private static void beginGolemRepair(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, int ingots, long now) {
		var waiting = SmithyRepairs.waitingAt(data.building());
		if (waiting.isEmpty()) {
			st.needsPlan = true;
			return;
		}
		st.repairGolem = waiting.get();
		st.repairIngotsLeft = ingots;
		int have = count(v, "minecraft:iron_ingot");
		if (have < ingots) {
			Map<String, Integer> want = Map.of("minecraft:iron_ingot", ingots - have);
			st.paused = st.paused == null ? SmithRuntime.Mode.IDLE : st.paused;
			beginFetch(level, v, data, st, want, null, now);
			return;
		}
		st.keep.add("minecraft:iron_ingot");
		st.mode = SmithRuntime.Mode.REPAIR_GOLEM;
		st.lastHeal = now - Balance.GOLEM_REPAIR_TICKS;
	}

	private static void repairGolemTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		if (!(level.getEntity(st.repairGolem) instanceof IronGolem g) || !g.isAlive()) {
			endGolemRepair(level, v, st);
			return;
		}
		int need = GolemRules.ingotsFor(g.getHealth(), g.getMaxHealth());
		if (need < 1) {
			endGolemRepair(level, v, st);
			return;
		}
		BlockPos at = g.blockPosition();
		if (v.distanceTo(g) > Balance.GOLEM_REPAIR_REACH) {
			if (!st.walk.active() || st.walk.target() == null || !st.walk.target().equals(at) || (now % 40 == 0)) st.walk.begin(at, 2.5, 2.0, now);
			st.walk.tick(v, now);
			return;
		}
		st.walk.release(v);
		if (now - st.lastHeal < Balance.GOLEM_REPAIR_TICKS) return;
		if (remove(v, "minecraft:iron_ingot", 1) < 1) {
			endGolemRepair(level, v, st);
			return;
		}
		g.heal(Balance.GOLEM_HEAL_PER_INGOT);
		st.lastHeal = now;
		v.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, g.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 1f, 1f);
		WorkXp.award(v, WorkUnit.ITEM_SMITHED, 1);
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, "GOLEM_REPAIR", "minecraft:iron_ingot"));
		st.repairIngotsLeft--;
	}

	private static void endGolemRepair(ServerLevel level, Villager v, SmithRuntime.State st) {
		st.keep.remove("minecraft:iron_ingot");
		st.repairGolem = null;
		SmithRuntime.Mode back = st.paused;
		st.paused = null;
		st.mode = back == null ? SmithRuntime.Mode.IDLE : back;
		st.needsPlan = st.mode == SmithRuntime.Mode.IDLE;
		st.walk.release(v);
		unlight(level, st);
	}

	// ---- repair gear -------------------------------------------------------------------------------

	private static void repairSlotTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		RepairScan.Hit hit = st.repairHit;
		if (hit == null) {
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		BlockPos at = hit.inPlace() ? hit.chest() : SmithyLayout.anvil(level.getServer(), data.building(), SmithyLayout.block(data.jobBlock()))
				.orElse(SmithyLayout.block(data.jobBlock()));
		if (!at(level, v, st, at, now)) return;
		BlockEntity be = level.getBlockEntity(hit.chest());
		if (!(be instanceof net.minecraft.world.Container c)) {
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		ItemStack stack = hit.slot() >= 0 && hit.slot() < c.getContainerSize() ? c.getItem(hit.slot()) : ItemStack.EMPTY;
		if (stack.isEmpty() || RepairRules.units(stack.getDamageValue(), stack.getMaxDamage()) < 1) {
			finishRepair(level, v, st);
			return;
		}
		if (count(v, hit.job().materialId()) < 1 && stockCount(level, hit.job().materialId()) < 1) {
			finishRepair(level, v, st);
			return;
		}
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		st.repairAcc += speed;
		if (st.repairAcc < Balance.SMITH_REPAIR_TICKS) return;
		st.repairAcc -= Balance.SMITH_REPAIR_TICKS;
		if (count(v, hit.job().materialId()) < 1) {
			ItemStack got = Stockpiles.withdraw(level, v.blockPosition(), StackKey.of(SmithCatalog.item(hit.job().materialId())), 1, v.getUUID());
			if (got.isEmpty()) {
				finishRepair(level, v, st);
				return;
			}
			v.getInventory().addItem(got);
		}
		if (remove(v, hit.job().materialId(), 1) < 1) return;
		stack.setDamageValue(RepairRules.after(stack.getDamageValue(), stack.getMaxDamage(), 1));
		v.swing(InteractionHand.MAIN_HAND);
		WorkXp.award(v, WorkUnit.ITEM_SMITHED, 1);
		SmithRuntime.get(level.getServer()).log(new SmithRuntime.Unit(v.getUUID(), now, "REPAIR", hit.job().itemId()));
		if (RepairRules.units(stack.getDamageValue(), stack.getMaxDamage()) < 1) finishRepair(level, v, st);
	}

	private static void finishRepair(ServerLevel level, Villager v, SmithRuntime.State st) {
		if (st.repairReturn && st.repairHit != null) {
			// the piece was fetched: put it back on the next tick of FETCH_REPAIR
		}
		st.repairHit = null;
		st.repairAcc = 0;
		st.mode = st.repairQueue.isEmpty() ? SmithRuntime.Mode.IDLE : SmithRuntime.Mode.FETCH_REPAIR;
		st.needsPlan = st.mode == SmithRuntime.Mode.IDLE;
		st.walk.release(v);
	}

	private static void fetchRepairTick(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		if (st.repairQueueIndex >= st.repairQueue.size()) {
			st.repairQueue = List.of();
			st.mode = SmithRuntime.Mode.IDLE;
			st.needsPlan = true;
			return;
		}
		RepairScan.Hit hit = st.repairQueue.get(st.repairQueueIndex);
		if (st.repairHit == null) {
			if (!at(level, v, st, hit.chest(), now)) return;
			BlockEntity be = level.getBlockEntity(hit.chest());
			if (!(be instanceof net.minecraft.world.Container c) || hit.slot() < 0 || hit.slot() >= c.getContainerSize()) {
				st.repairQueueIndex++;
				return;
			}
			ItemStack stack = c.getItem(hit.slot());
			if (stack.isEmpty()) {
				st.repairQueueIndex++;
				return;
			}
			c.setItem(hit.slot(), ItemStack.EMPTY);
			v.getInventory().addItem(stack);
			st.keep.add(SmithCatalog.id(stack.getItem()));
			st.repairHit = hit;
			st.repairReturn = true;
			st.mode = SmithRuntime.Mode.REPAIR_SLOT;
			return;
		}
		if (!at(level, v, st, hit.chest(), now)) return;
		ItemStack stack = takeStack(v, hit.job().itemId());
		if (!stack.isEmpty()) insert(level, hit.chest(), stack);
		if (!stack.isEmpty()) v.getInventory().addItem(stack);
		st.keep.remove(hit.job().itemId());
		st.repairHit = null;
		st.repairReturn = false;
		st.repairQueueIndex++;
	}

	// ---- cancel, surplus, adopt --------------------------------------------------------------------

	private static boolean due(SmithRuntime.State st, long now) {
		if (now - st.lastCancelCheck < Balance.SMITH_CANCEL_CHECK_TICKS) return false;
		st.lastCancelCheck = now;
		return true;
	}

	private static boolean cancelIf(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st, long now) {
		OrderKey key = st.fetchKey != null ? st.fetchKey : st.order != null ? st.order.key() : null;
		if (key == null) return false;
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		SlotPresence p = presence(level.getServer(), village.get(), key);
		CancelCheck check = GearDemandPlanner.cancel(p, st.cancelCount);
		st.cancelCount = check.consecutiveDefinite();
		if (check.reason().isEmpty()) return false;
		if (st.mode == SmithRuntime.Mode.CRAFT || st.mode == SmithRuntime.Mode.PREREQ) cancelSaved(level, v, data, st);
		else abortFetch(level, v, st, now);
		return true;
	}

	private static void cancelSaved(ServerLevel level, Villager v, WorkerData data, SmithRuntime.State st) {
		var saved = VillageClaims.current(level.getServer()).flatMap(vs -> vs.smithCraft(data.jobBlock()));
		saved.ifPresent(c -> SmithCrafts.refund(level.getServer(), data.jobBlock(), c));
		if (st.order != null) GearOrders.release(st.order.key());
		st.order = null;
		st.keep.clear();
		st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
		st.cancelCount = 0;
		st.walk.release(v);
	}

	private static void abortFetch(ServerLevel level, Villager v, SmithRuntime.State st, long now) {
		Stockpiles.reservations(level.getServer()).release(v.getUUID());
		st.wanted.clear();
		if (st.order != null && st.mode == SmithRuntime.Mode.FETCH) GearOrders.release(st.order.key());
		if (st.fetchKey != null && st.order == null) GearOrders.release(st.fetchKey);
		st.keep.clear();
		st.order = null;
		st.fetchKey = null;
		st.fetchToolId = null;
		st.fetchAnchor = null;
		st.buildFetch = false;
		st.batch = null;
		st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
		st.retryAt = now + 40;
		st.walk.release(v);
	}

	private static void cancelSurplus(MinecraftServer server, VillageState village) {
		DemandCensus.View view = DemandCensus.of(server, village);
		for (var e : view.open().entrySet()) {
			int needed = 0;
			for (GearDemand d : view.demands()) if (d.key().building().equals(e.getKey().building()) && d.key().need() == e.getKey().need()) needed++;
			for (OrderKey key : GearDemandPlanner.surplus(e.getValue(), needed)) {
				for (var craft : List.copyOf(village.smithCrafts().entrySet())) {
					if (!craft.getValue().craft().key().equals(key)) continue;
					SmithCrafts.refund(server, craft.getKey(), craft.getValue());
				}
				GearOrders.release(key);
			}
		}
	}

	private static void adopt(MinecraftServer server, Villager v, WorkerData data, VillageState village) {
		if (village.smithCraft(data.jobBlock()).isPresent()) return;
		List<HeartLocation> orphans = new ArrayList<>();
		for (var e : village.smithCrafts().entrySet()) {
			Optional<HeartLocation> smithy = SmithyLayout.smithyAt(server, e.getKey());
			if (smithy.isEmpty() || !smithy.get().equals(data.building())) continue;
			if (e.getKey().equals(data.jobBlock())) continue;
			UUID holder = SmithRuntime.holderFor(server, e.getKey());
			if (village.worker(holder).isEmpty()) orphans.add(e.getKey());
		}
		Optional<HeartLocation> taken = SmithPlanner.adoptFurnace(data.jobBlock(), false, orphans);
		if (taken.isEmpty()) return;
		HeartLocation from = taken.get();
		VillageClaims.data(server).update(vs -> vs.smithCraft(from).map(c -> vs.withoutSmithCraft(from).withSmithCraft(data.jobBlock(), c)).orElse(vs));
	}

	private static SlotPresence presence(MinecraftServer server, VillageState village, OrderKey key) {
		if (!ToolCensus.registered(village, key.building())) return SlotPresence.BUILDING_UNREGISTERED;
		var record = village.building(key.building());
		BuildingType type = record.flatMap(dev.villageheart.core.BuildingRecord::type).orElse(null);
		if (type == BuildingType.BARRACKS) {
			if (!ToolCensus.valid(server, key.building())) return SlotPresence.UNKNOWN;
			int guards = 0;
			for (var w : village.workers()) if (w.jobId().equals(JobType.GUARD.id()) && w.building().equals(key.building())) guards++;
			int real = 0;
			for (var chest : dev.villageheart.worker.job.CampContainers.of(server, key.building()).orElse(List.of())) {
				var c = chest.container();
				for (int i = 0; i < c.getContainerSize(); i++) {
					var info = dev.villageheart.core.gear.GearInfo.of(SmithCatalog.id(c.getItem(i).getItem())).orElse(null);
					if (info != null && info.need() == key.need() && !c.getItem(i).isDamaged()) real++;
				}
			}
			var open = GearOrders.openOrders(village, key.building(), key.need());
			return dev.villageheart.core.gear.GuardDemand.presence(key.building(), key.need(), guards, real, 0, open).getOrDefault(key, SlotPresence.COVERED);
		}
		JobType job = null;
		for (JobType j : JobType.values()) if (j.building() == type) job = j;
		if (job == null || job.tool().isEmpty()) return SlotPresence.UNKNOWN;
		CampDemand census = ToolCensus.of(server, village, key.building(), job, GearOrders.crafting(), dev.villageheart.worker.gear.GearLead.lead(server, village));
		if (census.isUnknown()) return SlotPresence.UNKNOWN;
		if (key.slot() >= ToolCensus.hired(village, key.building(), job).size()) return SlotPresence.HIRED_DROPPED;
		return dev.villageheart.core.gear.ToolDemand.presence(census, GearOrders.openOrders(village, key.building(), key.need()))
				.getOrDefault(key, SlotPresence.COVERED);
	}

	// ---- small helpers -----------------------------------------------------------------------------

	private static boolean at(ServerLevel level, Villager v, SmithRuntime.State st, BlockPos pos, long now) {
		if (pos == null) return false;
		if (!st.walk.active() || st.walk.target() == null || !st.walk.target().equals(pos)) st.walk.begin(pos, REACH, CLOSEST, now);
		WalkTo.State ws = st.walk.tick(v, now);
		if (ws == WalkTo.State.UNREACHABLE) {
			st.walk.clear();
			st.retryAt = now + 100;
			return false;
		}
		return ws == WalkTo.State.ARRIVED;
	}

	private static void light(ServerLevel level, Villager v, SmithRuntime.State st, BlockPos furnace, long now) {
		KitchenVisuals.light(level, furnace, true);
		st.litAt = furnace;
		st.lastWorkStamp = now;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(furnace));
	}

	private static void unlight(ServerLevel level, SmithRuntime.State st) {
		if (st.litAt != null) KitchenVisuals.light(level, st.litAt, false);
		st.litAt = null;
	}

	private static void abort(ServerLevel level, Villager v, SmithRuntime.State st, boolean returning) {
		Stockpiles.reservations(level.getServer()).release(v.getUUID());
		st.wanted.clear();
		st.returning = returning;
		st.mode = SmithRuntime.Mode.IDLE;
		st.needsPlan = true;
		unlight(level, st);
		st.walk.release(v);
	}

	private static void saveCraft(MinecraftServer server, WorkerData data, SmithRuntime.State st) {
		var saved = VillageClaims.current(server).flatMap(v -> v.smithCraft(data.jobBlock()));
		if (saved.isEmpty()) return;
		SmithCrafts.progress(server, data.jobBlock(), saved.get().craft().recipeId(), saved.get().craft().key(), (int) st.progressAcc);
	}

	private static void saveBuild(MinecraftServer server, WorkerData data, SmithRuntime.State st) {
		if (VillageClaims.current(server).map(v -> v.golemProgress(data.building())).orElse(-1) < 0) return;
		GolemBuilds.progress(server, data.building(), (int) Math.min(st.golemAcc, Balance.GOLEM_BUILD_TICKS));
	}

	private static int bank(MinecraftServer server, HeartLocation furnace) {
		return VillageClaims.current(server).map(v -> v.smokerHeat(furnace)).orElse(0);
	}

	private static void setBank(MinecraftServer server, HeartLocation furnace, int heat) {
		VillageClaims.data(server).update(v -> v.withSmokerHeat(furnace, Math.max(0, heat)));
	}

	private static int loadFuel(MinecraftServer server, Villager v, WorkerData data, int bank) {
		Map<String, Integer> have = carried(v);
		List<String> ids = new ArrayList<>();
		for (String id : have.keySet()) if (SmithCatalog.heatOrZero(id) > 0) ids.add(id);
		ids.sort(String::compareTo);
		if (ids.contains("minecraft:charcoal")) {
			ids.remove("minecraft:charcoal");
			ids.add(0, "minecraft:charcoal");
		}
		if (ids.contains("minecraft:coal")) {
			ids.remove("minecraft:coal");
			ids.add(0, "minecraft:coal");
		}
		for (String id : ids) {
			if (remove(v, id, 1) == 1) {
				int h = SmithCatalog.heatOrZero(id);
				setBank(server, data.jobBlock(), bank + h);
				return bank + h;
			}
		}
		return bank;
	}

	private static void addFuel(ServerLevel level, Villager v, Map<String, Integer> want, int heat) {
		var stock = Stockpiles.snapshot(level.getServer());
		String best = null;
		int bestHeat = 0;
		for (var t : stock.totals()) {
			if (!t.key().patch().isEmpty()) continue;
			int h = SmithCatalog.heatOrZero(t.key().id());
			if (h <= 0 || t.count() <= 0) continue;
			if (best == null || idRank(t.key().id()) < idRank(best)) {
				best = t.key().id();
				bestHeat = h;
			}
		}
		if (best == null || bestHeat <= 0) return;
		int items = (heat + bestHeat - 1) / bestHeat;
		want.merge(best, items, Integer::sum);
	}

	private static int idRank(String id) {
		if (id.equals("minecraft:charcoal")) return 0;
		if (id.equals("minecraft:coal")) return 1;
		return 2;
	}

	private static int count(Villager v, String id) {
		if (id == null || id.isEmpty()) return 0;
		int n = 0;
		var item = SmithCatalog.item(id);
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item) && s.getComponentsPatch().isEmpty()) n += s.getCount();
		}
		return n;
	}

	private static int remove(Villager v, String id, int n) {
		int left = n;
		var item = SmithCatalog.item(id);
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.is(item) || !s.getComponentsPatch().isEmpty()) continue;
			int take = Math.min(left, s.getCount());
			s.shrink(take);
			left -= take;
		}
		return n - left;
	}

	private static ItemStack takeStack(Villager v, String id) {
		var item = SmithCatalog.item(id);
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !s.is(item)) continue;
			inv.setItem(i, ItemStack.EMPTY);
			return s;
		}
		return ItemStack.EMPTY;
	}

	private static void give(Villager v, String id, int n) {
		if (n <= 0) return;
		ItemStack stack = new ItemStack(SmithCatalog.item(id), n);
		v.getInventory().addItem(stack);
		if (!stack.isEmpty() && v.level() instanceof ServerLevel level)
			Containers.dropItemStack(level, v.getX(), v.getY(), v.getZ(), stack);
	}

	private static int freeSlots(Villager v) {
		int n = 0;
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).isEmpty()) n++;
		return n;
	}

	private static long stockCount(ServerLevel level, String id) {
		long n = 0;
		for (var t : Stockpiles.snapshot(level.getServer()).totals()) if (t.key().id().equals(id) && t.key().patch().isEmpty()) n += t.count();
		return n;
	}

	private static int golemsInClaim(ServerLevel level, VillageState village) {
		int r = Balance.CLAIM_RADIUS;
		var box = new net.minecraft.world.phys.AABB(village.heart().x() - r, village.heart().y() - r, village.heart().z() - r,
				village.heart().x() + r + 1, village.heart().y() + r + 1, village.heart().z() + r + 1);
		return level.getEntities(EntityType.IRON_GOLEM, box, e -> e.isAlive()
				&& dev.villageheart.core.ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, e.blockPosition()))).size();
	}

	private static BlockPos firstChest(MinecraftServer server, HeartLocation building) {
		var chests = dev.villageheart.worker.job.CampContainers.of(server, building).orElse(List.of());
		for (var chest : chests) {
			var c = chest.container();
			for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return chest.pos();
		}
		return chests.isEmpty() ? null : chests.get(0).pos();
	}

	private static boolean insert(ServerLevel level, BlockPos chest, ItemStack stack) {
		BlockEntity be = level.getBlockEntity(chest);
		if (!(be instanceof net.minecraft.world.Container c)) return false;
		for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack have = c.getItem(i);
			if (have.isEmpty()) {
				c.setItem(i, stack.copy());
				stack.setCount(0);
				return true;
			}
			if (ItemStack.isSameItemSameComponents(have, stack) && have.getCount() < have.getMaxStackSize()) {
				int n = Math.min(stack.getCount(), have.getMaxStackSize() - have.getCount());
				have.grow(n);
				stack.shrink(n);
				if (stack.isEmpty()) return true;
			}
		}
		return stack.isEmpty();
	}

	private static void tidyWorn(ServerLevel level, WorkerData data, OrderKey key) {
		JobType job = null;
		for (JobType j : JobType.values()) if (j.building() == villageType(level.getServer(), key.building())) job = j;
		if (job == null || job.tool().isEmpty()) return;
		var chests = dev.villageheart.worker.job.CampContainers.of(level.getServer(), key.building()).orElse(List.of());
		if (chests.isEmpty()) return;
		Set<Integer> assigned = new HashSet<>();
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return;
		List<UUID> workers = ToolCensus.hired(village.get(), key.building(), job);
		// the best tools are the ones a worker would lease; everything worn and unassigned goes to the Smithy
		for (UUID id : workers) {
			Villager worker = Workers.findLoaded(level.getServer(), id).orElse(null);
			if (worker == null) continue;
			var lease = CampTools.lease(level.getServer(), worker, job.tool().get().kind(), null);
			if (lease instanceof CampTools.Held held) assigned.add(System.identityHashCode(held.lease().stack()));
		}
		BlockPos smithyChest = SmithyLayout.chest(level.getServer(), data.building(), SmithyLayout.block(data.jobBlock())).orElse(null);
		if (smithyChest == null) return;
		for (var chest : chests) {
			var c = chest.container();
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty() || !dev.villageheart.worker.gear.ToolStacks.worn(s, job)) continue;
				if (assigned.contains(System.identityHashCode(s))) continue;
				ItemStack moving = s.copy();
				c.setItem(i, ItemStack.EMPTY);
				if (!insert(level, smithyChest, moving) && !moving.isEmpty()) c.setItem(i, moving);
			}
		}
	}

	private static BuildingType villageType(MinecraftServer server, HeartLocation building) {
		return VillageClaims.current(server).flatMap(v -> v.building(building)).flatMap(dev.villageheart.core.BuildingRecord::type).orElse(null);
	}

	private static String buildingName(MinecraftServer server, HeartLocation building) {
		BuildingType type = villageType(server, building);
		if (type == null) return "Camp";
		return switch (type) {
			case LUMBER_CAMP -> "Lumber Camp";
			case MINE -> "Mine";
			case FARMHOUSE -> "Farmhouse";
			case HUNTING_LODGE -> "Hunting Lodge";
			case BARRACKS -> "Barracks";
			default -> "Camp";
		};
	}

	private static void expireBlocks(SmithRuntime.State st, long now) {
		st.blockedUntil.entrySet().removeIf(e -> e.getValue() <= now);
	}

	private static RepairScan.Hit match(List<RepairScan.Hit> hits, SmithFacts.RepairJob job) {
		for (RepairScan.Hit h : hits) if (h.job().itemId().equals(job.itemId()) && h.job().damage() == job.damage()) return h;
		return hits.isEmpty() ? null : hits.get(0);
	}

	private static boolean take(List<ItemCount> items, String id, int n) {
		int have = takeCount(items, id);
		if (have < n) return false;
		int left = n;
		for (int i = 0; i < items.size() && left > 0; i++) {
			ItemCount c = items.get(i);
			if (!c.id().equals(id)) continue;
			int used = Math.min(left, c.count());
			items.set(i, new ItemCount(id, c.count() - used));
			left -= used;
		}
		items.removeIf(c -> c.count() == 0);
		return true;
	}

	private static boolean takePlanks(List<ItemCount> items, int n) {
		int have = 0;
		for (ItemCount c : items) if (c.id().endsWith("_planks")) have += c.count();
		if (have < n) return false;
		int left = n;
		for (int i = 0; i < items.size() && left > 0; i++) {
			ItemCount c = items.get(i);
			if (!c.id().endsWith("_planks")) continue;
			int used = Math.min(left, c.count());
			items.set(i, new ItemCount(c.id(), c.count() - used));
			left -= used;
		}
		items.removeIf(c -> c.count() == 0);
		return true;
	}

	private static int takeCount(List<ItemCount> items, String id) {
		int n = 0;
		for (ItemCount c : items) if (c.id().equals(id)) n += c.count();
		return n;
	}

	private static void add(List<ItemCount> items, String id, int n) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).id().equals(id)) {
				items.set(i, new ItemCount(id, items.get(i).count() + n));
				return;
			}
		}
		items.add(new ItemCount(id, n));
	}

	private static boolean isFuelId(String id) { return SmithCatalog.heatOrZero(id) > 0; }

}
