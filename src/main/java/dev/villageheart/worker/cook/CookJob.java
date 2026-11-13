package dev.villageheart.worker.cook;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.cook.BatchMath;
import dev.villageheart.core.cook.CookKeep;
import dev.villageheart.core.cook.CookPlanner;
import dev.villageheart.core.cook.CookPlanner.Batch;
import dev.villageheart.core.cook.CookPlanner.CookDecision;
import dev.villageheart.core.cook.CookPlanner.CookHint;
import dev.villageheart.core.cook.CookPlanner.Stock;
import dev.villageheart.core.cook.CookPlanner.TripState;
import dev.villageheart.core.cook.CookRecipe;
import dev.villageheart.core.cook.CookRecipes;
import dev.villageheart.core.cook.FuelTable;
import dev.villageheart.core.cook.Ingredient;
import dev.villageheart.core.cook.RecipeKind;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.DepositTrip;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.cook.CookRuntime.Action;
import dev.villageheart.worker.cook.CookRuntime.CookState;
import dev.villageheart.worker.job.CampContainers;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The cook's work, one call per tick while WORK is active (M13 plan 3.2 and 3.4). {@link CookPlanner} decides; this class carries it out:
 * FETCH the batch's inputs and fuel from the stockpile (reserved first), PREP at the crafting table for bread and soup, COOK at the smoker on a
 * {@link dev.villageheart.core.job.WorkClock}. Every finished unit is one atomic change (inputs out, output in, 200 heat out of the smoker's
 * bank), so an interruption loses at most the unit in progress. All of it runs inside {@code VillageTicker.section()}.
 */
public final class CookJob {
	private static final String JOB = "cook";
	private static final int PLAN_INTERVAL = 20;
	private static final int VALID_INTERVAL = 20;
	private static final double ARRIVE = Balance.COOK_REACH;
	/** The cook stands next to its smoker, not two blocks off: a cook that idles in the Kitchen doorway blocks the other one (two-cook flake). */
	private static final double SMOKER_REACH = 1.6;
	private static final double CLOSEST = 3.0;
	private static final boolean TRACE = Boolean.getBoolean("villageheart.cook.trace");

	private CookJob() {}

	private static void trace(String fmt, Object... args) {
		if (TRACE) VillageHeart.LOG.info("[cook] " + fmt, args);
	}

	// ---- entry points ------------------------------------------------------------------------------

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.COOK) return;
		MinecraftServer server = level.getServer();
		CookRuntime rt = CookRuntime.get(server);
		CookState st = rt.state(v.getUUID());
		st.lastTick = now;
		if (Workers.tripRunning(v)) return; // a deposit trip owns the walk
		if (now - st.lastValid >= VALID_INTERVAL) {
			st.lastValid = now;
			st.valid = campValid(level, v);
			if (!st.valid && (st.batch != null || st.action != Action.IDLE)) abort(level, v, st, true);
		}
		if (st.action == Action.IDLE && now >= st.retryAt && (st.needsPlan || now - st.lastPlan >= PLAN_INTERVAL)) plan(level, v, data, rt, st, now);
		run(level, v, data, st, now);
	}

	/** The behaviour ended (night, panic, raid, fired): the reservations go back and the smoker's light goes out. */
	public static void stop(ServerLevel level, Villager v) {
		CookRuntime rt = CookRuntime.get(level.getServer());
		CookState st = rt.workers.get(v.getUUID());
		if (st == null) return;
		Stockpiles.reservations(level.getServer()).release(v.getUUID());
		st.wanted.clear();
		st.walk.release(v);
		unlight(level, st);
		if (st.action == Action.FETCH) st.action = Action.IDLE;
		st.needsPlan = true;
	}

	/** The cook was fired or died: its task, reservations and light are forgotten (its heat bank stays with the smoker). */
	public static void forget(MinecraftServer server, UUID cook) {
		CookRuntime rt = CookRuntime.get(server);
		CookState st = rt.workers.remove(cook);
		Stockpiles.reservations(server).release(cook);
		if (st != null && st.litAt != null) {
			for (ServerLevel l : server.getAllLevels()) KitchenVisuals.light(l, st.litAt, false);
		}
	}

	/** What a cook never hands in to Storage (see {@link CookKeep}). */
	public static int keep(Villager v, Item item) {
		if (!(v.level() instanceof ServerLevel level)) return 0;
		CookState st = CookRuntime.get(level.getServer()).state(v.getUUID());
		boolean home = DaySchedule.phaseAt(level.getDayTime()) != DayPhase.WORK;
		return CookKeep.keep(CookCatalog.id(item), CookCatalog.isFuel(item), carried(v), Optional.ofNullable(st.batch), st.returning, home);
	}

	/** True when the cook has anything to hand in (the caller has checked the load): a finished batch's outputs, leftovers, foreign items. */
	public static boolean depositNow(Villager v) { return true; }

	/** Test hook: the cook holds the inputs of a batch it is already running (as after the fetch). */
	public static void setBatchForTest(MinecraftServer server, UUID cook, String recipeId, int units) {
		CookState st = CookRuntime.get(server).state(cook);
		CookRecipe r = CookRecipes.ALL.stream().filter(x -> x.id().equals(recipeId)).findFirst().orElseThrow();
		st.batch = new Batch(r, units, "");
		st.returning = false;
		st.prepDone = true;
		st.needsPlan = true;
	}

	/** Test hook: the batch in progress as (recipe id, units left), or null. */
	public static String batchForTest(MinecraftServer server, UUID cook) {
		CookState st = CookRuntime.get(server).workers.get(cook);
		return st == null || st.batch == null ? null : st.batch.recipe().id() + " x" + st.batch.units();
	}

	/** Test hook: the cook plans on its next tick. */
	public static void replanForTest(MinecraftServer server, UUID cook) {
		CookState st = CookRuntime.get(server).state(cook);
		st.needsPlan = true;
		st.lastPlan = Long.MIN_VALUE;
	}

	// ---- helpers -----------------------------------------------------------------------------------

	private static BlockPos bp(HeartLocation l) { return new BlockPos(l.x(), l.y(), l.z()); }

	private static boolean campValid(ServerLevel level, Villager v) {
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		var rec = village.get().worker(v.getUUID());
		return rec.isPresent() && Workers.status(level.getServer(), rec.get()) != WorkerStatus.WORKPLACE_INVALID;
	}

	/** Component-free stacks carried, item id to count. */
	static Map<String, Integer> carried(Villager v) {
		Map<String, Integer> out = new HashMap<>();
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (CookCatalog.plain(s)) out.merge(CookCatalog.id(s.getItem()), s.getCount(), Integer::sum);
		}
		return out;
	}

	private static int freeSlots(Villager v) {
		int free = 0;
		for (int i = 0; i < v.getInventory().getContainerSize(); i++) if (v.getInventory().getItem(i).isEmpty()) free++;
		return free;
	}

	private static int remove(Villager v, String itemId, int n) {
		Item item = CookCatalog.item(itemId);
		SimpleContainer inv = v.getInventory();
		int left = n;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (!CookCatalog.plain(s) || s.getItem() != item) continue;
			int take = Math.min(left, s.getCount());
			s.shrink(take);
			left -= take;
		}
		inv.setChanged();
		return n - left;
	}

	private static int bank(MinecraftServer server, HeartLocation smoker) {
		return VillageClaims.current(server).map(s -> s.smokerHeat(smoker)).orElse(0);
	}

	private static void setBank(MinecraftServer server, HeartLocation smoker, int heat) {
		VillageClaims.data(server).update(s -> s.withSmokerHeat(smoker, Math.max(0, heat)));
	}

	private static double dist(BlockPos a, BlockPos b) { return Math.sqrt(a.distSqr(b)); }

	private static void unlight(ServerLevel level, CookState st) {
		if (st.litAt != null) {
			KitchenVisuals.light(level, st.litAt, false);
			st.litAt = null;
		}
	}

	/** Ends whatever the cook is doing; {@code handBack}: nothing is kept (the Kitchen is invalid). */
	private static void abort(ServerLevel level, Villager v, CookState st, boolean handBack) {
		Stockpiles.reservations(level.getServer()).release(v.getUUID());
		st.wanted.clear();
		st.walk.release(v);
		unlight(level, st);
		st.batch = null;
		st.returning = handBack;
		st.action = Action.IDLE;
		st.needsPlan = true;
	}

	// ---- planning ----------------------------------------------------------------------------------

	private static void plan(ServerLevel level, Villager v, WorkerData data, CookRuntime rt, CookState st, long now) {
		MinecraftServer server = level.getServer();
		st.lastPlan = now;
		st.needsPlan = false;
		Stock stock = stock(level, v, data, st, now);
		boolean depositNow = DepositTrip.load(v) > 0;
		CookDecision d = CookPlanner.decide(stock, depositNow);
		trace("{} @{} decision {} (batch {} bank {} carried {})", v.getId(), now, d, st.batch, stock.bank(), stock.carried());
		WorkerStatus status = null;
		switch (d) {
			case CookDecision.Yield y -> {
				// the deposit trip runs by itself; stand at the smoker meanwhile
			}
			case CookDecision.Cook c -> {
				st.returning = false;
				if (st.batch == null || st.batch.recipe() != c.r()) {
					st.batch = new Batch(c.r(), c.units(), "");
					st.prepDone = c.r().kind() != RecipeKind.CRAFT;
					st.prepStart = -1;
					st.prepAt = -1;
				} else {
					st.batch = new Batch(c.r(), Math.min(st.batch.units(), c.units()), "");
				}
				st.action = c.r().kind() == RecipeKind.CRAFT && !st.prepDone ? Action.PREP : Action.COOK;
				st.walk.release(v);
			}
			case CookDecision.Fetch f -> {
				st.returning = false;
				startFetch(level, v, data, st, f, now);
			}
			case CookDecision.Idle i -> {
				st.returning = i.returning();
				if (i.returning()) st.batch = null;
				status = switch (i.hint()) {
					case NEEDS_FUEL -> WorkerStatus.NEEDS_FUEL;
					case NO_WORK -> WorkerStatus.NO_WORK;
					default -> null;
				};
			}
		}
		WorkerStatuses.set(v.getUUID(), status);
	}

	private static Stock stock(ServerLevel level, Villager v, WorkerData data, CookState st, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		Reservations<StackKey> res = Stockpiles.reservations(server);
		Map<String, Long> available = new HashMap<>();
		Map<String, Integer> fuelHeat = new HashMap<>();
		for (StockSnapshot.Total<StackKey> t : Stockpiles.snapshot(server).totals()) {
			StackKey k = t.key();
			if (!CookCatalog.plain(k)) continue;
			String id = k.id();
			boolean fuel = CookCatalog.heat(k.item()).isPresent();
			if (!fuel && !CookRecipes.inputIds().contains(id)) continue;
			long free = Math.max(0, t.count() - (res.reserved(k) - res.reservedBy(me, k)));
			available.merge(id, free, Long::sum);
			if (fuel) fuelHeat.put(id, CookCatalog.heat(k.item()).getAsInt());
		}
		Map<String, Integer> carried = carried(v);
		for (String id : carried.keySet()) {
			Item item = CookCatalog.item(id);
			if (CookCatalog.isFuel(item)) fuelHeat.put(id, CookCatalog.heat(item).getAsInt());
		}
		BlockPos smoker = bp(data.jobBlock());
		int d = Stockpiles.nearestHolding(level, smoker, k -> true).map(a -> (int) Math.ceil(dist(a, smoker))).orElse(0);
		int walk = 3 * d * Balance.COOK_WALK_TICKS_PER_BLOCK + Balance.COOK_WALK_SLACK;
		int craftWalk = 0;
		Optional<BlockPos> table = table(server, data, smoker);
		if (table.isPresent()) craftWalk = 2 * (int) Math.ceil(dist(table.get(), smoker)) * Balance.COOK_WALK_TICKS_PER_BLOCK;
		WorkerStatus tripStatus = Workers.tripStatus(me);
		TripState trip = tripStatus == WorkerStatus.STORAGE_FULL ? TripState.FULL : tripStatus == WorkerStatus.STORAGE_UNREACHABLE ? TripState.UNREACHABLE : TripState.NONE;
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		return new Stock(available, carried, fuelHeat, BatchMath.fuelOrder(fuelHeat.keySet()), bank(server, data.jobBlock()),
				Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS), speed, st.valid, trip, st.returning, freeSlots(v), walk, craftWalk,
				Optional.ofNullable(st.batch));
	}

	/** The crafting table of the cook's Kitchen nearest to its smoker, if the scan has one. */
	private static Optional<BlockPos> table(MinecraftServer server, WorkerData data, BlockPos smoker) {
		var view = CampContainers.building(server, data.building());
		if (view.isEmpty() || view.get().latestScan().isEmpty()) return Optional.empty();
		BlockPos best = null;
		for (Pos p : view.get().latestScan().get().positions(BuildingRequirements.CRAFTING_TABLE)) {
			BlockPos b = new BlockPos(p.x(), p.y(), p.z());
			if (best == null || b.distSqr(smoker) < best.distSqr(smoker)) best = b;
		}
		return Optional.ofNullable(best);
	}

	// ---- fetch -------------------------------------------------------------------------------------

	private static void startFetch(ServerLevel level, Villager v, WorkerData data, CookState st, CookDecision.Fetch f, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		Reservations<StackKey> res = Stockpiles.reservations(server);
		res.release(me);
		st.wanted.clear();
		// reserve what is wanted in one step (the server thread is single, so a second cook planning in this tick sees this claim)
		Map<String, Integer> asked = new LinkedHashMap<>(f.inputs());
		if (f.fuel() > 0) asked.put(f.fuelId(), f.fuel());
		Map<String, Integer> got = new HashMap<>();
		for (Map.Entry<String, Integer> e : asked.entrySet()) {
			StackKey key = StackKey.of(CookCatalog.item(e.getKey()));
			int n = (int) Math.min(e.getValue(), Stockpiles.available(server, key, me));
			if (n > 0) {
				res.set(me, key, n);
				got.put(e.getKey(), n);
			}
		}
		// recompute the batch from what was actually reserved
		Map<String, Integer> have = new HashMap<>(carried(v));
		got.forEach((id, n) -> have.merge(id, n, Integer::sum));
		int units = Math.min(f.units(), f.r().unitsFrom(have));
		int bank = bank(server, data.jobBlock());
		if (units >= 1 && f.fuel() > 0) {
			int burn = CookCatalog.heat(CookCatalog.item(f.fuelId())).getAsInt();
			units = Math.min(units, (bank + have.getOrDefault(f.fuelId(), 0) * burn) / FuelTable.HEAT_PER_ITEM);
		}
		if (units < 1) {
			res.release(me);
			st.retryAt = now + 100; // the stock changed under the plan: look again in a few seconds
			st.needsPlan = true;
			return;
		}
		// trim the reservations to the shrunken batch
		for (Ingredient i : f.r().inputs()) {
			int need = Math.max(0, i.count() * units - carried(v).getOrDefault(i.itemId(), 0));
			StackKey key = StackKey.of(CookCatalog.item(i.itemId()));
			if (need > 0) {
				res.set(me, key, need);
				st.wanted.put(key, need);
			} else {
				res.set(me, key, 0);
			}
		}
		if (f.fuel() > 0) {
			int burn = CookCatalog.heat(CookCatalog.item(f.fuelId())).getAsInt();
			int carriedHeat = carried(v).getOrDefault(f.fuelId(), 0) * burn;
			int need = Math.min(got.getOrDefault(f.fuelId(), 0), BatchMath.fuelToFetch(units, bank + carriedHeat, burn));
			StackKey key = StackKey.of(CookCatalog.item(f.fuelId()));
			res.set(me, key, need);
			if (need > 0) st.wanted.put(key, need);
		}
		st.batch = new Batch(f.r(), units, f.fuelId());
		st.prepDone = f.r().kind() != RecipeKind.CRAFT;
		st.prepStart = -1;
		st.prepAt = -1;
		st.stops = 0;
		st.stop = null;
		st.action = Action.FETCH;
		// deadline: the straight legs through the stops and back to the smoker
		double legs = 0;
		BlockPos prev = v.blockPosition();
		for (StackKey key : st.wanted.keySet()) {
			Optional<BlockPos> a = Stockpiles.nearestHolding(level, prev, key, me);
			if (a.isPresent()) {
				legs += dist(prev, a.get());
				prev = a.get();
			}
		}
		legs += dist(prev, bp(data.jobBlock()));
		st.fetchDeadline = now + Balance.COOK_FETCH_TIMEOUT + (long) (Balance.COOK_FETCH_TICKS_PER_BLOCK * legs);
		trace("{} fetches {} for {} x{} (deadline {})", v.getId(), st.wanted, f.r().id(), units, st.fetchDeadline);
	}

	private static void fetchTick(ServerLevel level, Villager v, WorkerData data, CookState st, long now) {
		UUID me = v.getUUID();
		if (now > st.fetchDeadline) {
			trace("{} fetch timed out", v.getId());
			endFetch(level, v, data, st);
			return;
		}
		if (st.wanted.isEmpty()) {
			endFetch(level, v, data, st);
			return;
		}
		Map.Entry<StackKey, Integer> first = st.wanted.entrySet().iterator().next();
		StackKey key = first.getKey();
		if (st.stop == null) {
			Optional<BlockPos> a = Stockpiles.nearestHolding(level, v.blockPosition(), key, me);
			if (a.isEmpty()) {
				st.wanted.remove(key); // nobody holds it any more: the batch shrinks
				return;
			}
			st.stop = a.get();
			st.walk.begin(st.stop, ARRIVE, CLOSEST, now);
		}
		WalkTo.State ws = st.walk.tick(v, now);
		if (ws == WalkTo.State.WALKING) return;
		if (ws == WalkTo.State.UNREACHABLE) {
			trace("{} cannot reach the Storage at {}: {}", v.getId(), st.stop, st.walk.reason());
			st.wanted.clear();
			endFetch(level, v, data, st);
			return;
		}
		int need = first.getValue();
		while (need > 0) {
			ItemStack got = Stockpiles.withdrawAt(level, st.stop, key, need, me);
			if (got.isEmpty()) break;
			int n = got.getCount();
			ItemStack rest = v.getInventory().addItem(got);
			if (!rest.isEmpty()) Stockpiles.depositHere(level, st.stop, rest);
			need -= n - rest.getCount();
			if (!rest.isEmpty()) break;
		}
		v.getInventory().setChanged();
		st.stop = null;
		st.walk.release(v);
		if (need <= 0) {
			st.wanted.remove(key);
		} else if (++st.stops >= Balance.COOK_MAX_STOPS) {
			st.wanted.remove(key);
		} else {
			first.setValue(need); // short here: look for the rest at the next Storage that holds it
		}
	}

	/** The fetch is over (complete, short or timed out): the reservations go back and the batch shrinks to what the cook holds. */
	private static void endFetch(ServerLevel level, Villager v, WorkerData data, CookState st) {
		MinecraftServer server = level.getServer();
		Stockpiles.reservations(server).release(v.getUUID());
		st.wanted.clear();
		st.stop = null;
		st.walk.release(v);
		st.action = Action.IDLE;
		st.needsPlan = true;
		if (st.batch == null) return;
		Map<String, Integer> have = carried(v);
		int units = Math.min(st.batch.units(), st.batch.recipe().unitsFrom(have));
		int heat = bank(server, data.jobBlock());
		for (Map.Entry<String, Integer> e : have.entrySet()) {
			Item item = CookCatalog.item(e.getKey());
			if (CookCatalog.isFuel(item)) heat += e.getValue() * CookCatalog.heat(item).getAsInt();
		}
		units = Math.min(units, heat / FuelTable.HEAT_PER_ITEM);
		if (units < 1) {
			st.batch = null; // nothing usable arrived: what was fetched goes back with the next deposit trip
			return;
		}
		st.batch = new Batch(st.batch.recipe(), units, "");
		st.action = st.batch.recipe().kind() == RecipeKind.CRAFT && !st.prepDone ? Action.PREP : Action.COOK;
	}

	// ---- running -----------------------------------------------------------------------------------

	private static void run(ServerLevel level, Villager v, WorkerData data, CookState st, long now) {
		BlockPos smoker = bp(data.jobBlock());
		switch (st.action) {
			case FETCH -> fetchTick(level, v, data, st, now);
			case PREP -> prepTick(level, v, data, st, smoker, now);
			case COOK -> cookTick(level, v, data, st, smoker, now);
			case IDLE -> idleTick(level, v, st, smoker, now);
		}
	}

	private static void idleTick(ServerLevel level, Villager v, CookState st, BlockPos smoker, long now) {
		unlight(level, st);
		if (dev.villageheart.worker.job.WorkstationStand.at(v, smoker)) st.walk.release(v);
		else {
			BlockPos spot = dev.villageheart.worker.job.WorkstationStand.spot(level, smoker, v.position());
			if (!st.walk.active() || !st.walk.target().equals(spot)) st.walk.begin(spot, spot.equals(smoker) ? SMOKER_REACH : 1.0, now);
			if (st.walk.tick(v, now) == WalkTo.State.UNREACHABLE) st.walk.clear();
		}
		if ((now + v.getId()) % 20 == 0 && dev.villageheart.worker.job.WorkstationStand.at(v, smoker)) {
			v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
		}
	}

	private static void prepTick(ServerLevel level, Villager v, WorkerData data, CookState st, BlockPos smoker, long now) {
		if (st.table == null) {
			Optional<BlockPos> t = table(level.getServer(), data, smoker);
			if (t.isEmpty()) {
				skipPrep(v, st);
				return;
			}
			st.table = t.get();
			st.prepStart = now;
			st.prepAt = -1;
			st.walk.begin(st.table, ARRIVE, CLOSEST, now);
		}
		WalkTo.State ws = st.walk.tick(v, now);
		if (ws == WalkTo.State.UNREACHABLE || (ws == WalkTo.State.WALKING && now - st.prepStart > Balance.COOK_PREP_TIMEOUT)) {
			skipPrep(v, st);
			return;
		}
		if (ws == WalkTo.State.WALKING) return;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(st.table));
		if (st.prepAt < 0) {
			st.prepAt = now;
			v.swing(InteractionHand.MAIN_HAND);
		}
		if (now - st.prepAt >= Balance.COOK_PREP_TICKS) {
			st.prepDone = true;
			st.table = null;
			st.walk.release(v);
			st.action = Action.COOK;
		}
	}

	private static void skipPrep(Villager v, CookState st) {
		WorkStats.add(JOB, "prep_skipped", 1);
		st.prepDone = true;
		st.table = null;
		st.walk.release(v);
		st.action = Action.COOK;
	}

	private static void finishBatch(ServerLevel level, Villager v, CookState st, boolean counted) {
		if (counted) WorkStats.add(JOB, "batches", 1);
		st.batch = null;
		st.action = Action.IDLE;
		st.needsPlan = true;
		st.walk.release(v);
		unlight(level, st);
	}

	private static void cookTick(ServerLevel level, Villager v, WorkerData data, CookState st, BlockPos smoker, long now) {
		MinecraftServer server = level.getServer();
		if (st.batch == null) {
			finishBatch(level, v, st, false);
			return;
		}
		if (dev.villageheart.worker.job.WorkstationStand.at(v, smoker)) st.walk.release(v);
		else {
			BlockPos spot = dev.villageheart.worker.job.WorkstationStand.spot(level, smoker, v.position());
			if (!st.walk.active() || !st.walk.target().equals(spot)) st.walk.begin(spot, spot.equals(smoker) ? SMOKER_REACH : 1.0, now);
		}
		WalkTo.State ws = dev.villageheart.worker.job.WorkstationStand.at(v, smoker) ? WalkTo.State.ARRIVED : st.walk.tick(v, now);
		if (ws == WalkTo.State.UNREACHABLE) {
			trace("{} cannot reach its smoker: {}", v.getId(), st.walk.reason());
			st.batch = null; // what it holds is handed in by the next trip
			st.retryAt = now + 200;
			finishBatch(level, v, st, false);
			return;
		}
		if (ws == WalkTo.State.WALKING) return;
		CookRecipe r = st.batch.recipe();
		if (r.unitsFrom(carried(v)) < 1) {
			finishBatch(level, v, st, true);
			return;
		}
		int bank = bank(server, data.jobBlock());
		if (bank < FuelTable.HEAT_PER_ITEM) {
			bank = loadFuel(server, v, data, bank);
			if (bank < FuelTable.HEAT_PER_ITEM) {
				finishBatch(level, v, st, true);
				return;
			}
		}
		double speed = WorkSpeed.untooled(data.level(), data.needs());
		if (speed <= 0) return;
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(smoker));
		if (st.litAt == null || !KitchenVisuals.isLit(level, smoker)) {
			KitchenVisuals.light(level, smoker, true);
			st.litAt = smoker;
		}
		st.lastCookTick = now;
		v.getBrain().setMemory(MemoryModuleType.LAST_WORKED_AT_POI, now);
		int units = st.clock.tick(speed);
		for (int u = 0; u < units && st.batch != null; u++) {
			if (!completeUnit(level, v, data, st, smoker, now)) {
				finishBatch(level, v, st, true);
				return;
			}
			if (st.batch != null && bank(server, data.jobBlock()) < FuelTable.HEAT_PER_ITEM) {
				if (loadFuel(server, v, data, bank(server, data.jobBlock())) < FuelTable.HEAT_PER_ITEM) {
					finishBatch(level, v, st, true);
					return;
				}
			}
		}
	}

	/** Burns one carried fuel item into the smoker's bank (the first in the fuel order); returns the bank afterwards. */
	private static int loadFuel(MinecraftServer server, Villager v, WorkerData data, int bank) {
		Map<String, Integer> have = carried(v);
		Map<String, Integer> heat = new HashMap<>();
		for (String id : have.keySet()) {
			Item item = CookCatalog.item(id);
			if (CookCatalog.isFuel(item)) heat.put(id, CookCatalog.heat(item).getAsInt());
		}
		for (String id : BatchMath.fuelOrder(heat.keySet())) {
			if (remove(v, id, 1) == 1) {
				int h = heat.get(id);
				setBank(server, data.jobBlock(), bank + h);
				WorkStats.add(JOB, "fuel_heat", h);
				return bank + h;
			}
		}
		return bank;
	}

	/** One output item: inputs out, output in, 200 heat out of the bank; false when the batch cannot go on. */
	private static boolean completeUnit(ServerLevel level, Villager v, WorkerData data, CookState st, BlockPos smoker, long now) {
		MinecraftServer server = level.getServer();
		CookRecipe r = st.batch.recipe();
		Map<String, Integer> have = carried(v);
		if (r.unitsFrom(have) < 1) return false;
		int bank = bank(server, data.jobBlock());
		if (bank < FuelTable.HEAT_PER_ITEM) return false;
		ItemStack out = new ItemStack(CookCatalog.item(r.outputId()), 1);
		// the output must fit once the inputs are out: nothing is ever dropped into the world
		for (Ingredient i : r.inputs()) remove(v, i.itemId(), i.count());
		if (!v.getInventory().canAddItem(out)) {
			for (Ingredient i : r.inputs()) v.getInventory().addItem(new ItemStack(CookCatalog.item(i.itemId()), i.count()));
			return false;
		}
		v.getInventory().addItem(out);
		v.getInventory().setChanged();
		setBank(server, data.jobBlock(), bank - FuelTable.HEAT_PER_ITEM);
		v.swing(InteractionHand.MAIN_HAND);
		WorkStats.add(JOB, "items", 1);
		WorkStats.add(JOB, r.outputId().substring("minecraft:".length()), 1);
		WorkXp.award(v, WorkUnit.ITEM_COOKED, 1);
		CookRuntime.get(server).log(new CookRuntime.UnitRecord(v.getUUID(), now, smoker, r.outputId()));
		int left = st.batch.units() - 1;
		if (left <= 0) {
			finishBatch(level, v, st, true);
		} else {
			st.batch = new Batch(r, left, st.batch.fuelId());
		}
		return true;
	}
}
