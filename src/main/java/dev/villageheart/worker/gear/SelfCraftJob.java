package dev.villageheart.worker.gear;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.gear.CampDemand;
import dev.villageheart.core.gear.CraftPlan;
import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OpenOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.PendingFetch;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.core.gear.SelfCraftDecision;
import dev.villageheart.core.gear.SelfCraftFacts;
import dev.villageheart.core.gear.SelfCraftPlanner;
import dev.villageheart.core.gear.SlotPresence;
import dev.villageheart.core.gear.SlotState;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.TierChoice;
import dev.villageheart.core.gear.ToolDemand;
import dev.villageheart.core.gear.ToolRecipe;
import dev.villageheart.core.gear.ToolRecipes;
import dev.villageheart.core.gear.WorkerToolState;
import dev.villageheart.core.job.ToolAllocation;
import dev.villageheart.core.job.ToolRequirement;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.CampContainers;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A tool worker makes its own wooden or stone tool (M19a plan 3.2). {@link #tick} runs at the top of each tool job's tick and returns true
 * when the craft owns this tick (the job's own tick is then skipped). The decisions are the pure {@link SelfCraftPlanner}'s; this class
 * gathers its facts, walks, reserves and withdraws the materials (the commit), works at the job block and finishes the tool.
 * All of it runs inside {@code VillageTicker.section()} (the job behaviours' sections).
 */
public final class SelfCraftJob {
	private static final double CHEST_REACH = 2.0;
	private static final int PLAN_INTERVAL = 20;

	private SelfCraftJob() {}

	// ---- entry points ------------------------------------------------------------------------------

	/** True when the self-craft owns this tick. Call after the job's trip check; false means the job carries on as usual. */
	public static boolean tick(ServerLevel level, Villager v, long now) {
		if (!SelfCraftSwitch.enabled()) return false;
		WorkerData data = WorkerAttachments.get(v).orElse(null);
		if (data == null) return false;
		JobType job = data.job();
		Optional<ToolRequirement> req = job.tool();
		if (req.isEmpty()) return false;
		MinecraftServer server = level.getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty() || village.get().worker(v.getUUID()).isEmpty()) return false;
		SelfCraftRuntime rt = SelfCraftRuntime.get(server);
		SelfCraftRuntime.State st = rt.state(v.getUUID());
		if (st.decision == null || now - st.lastPlan >= PLAN_INTERVAL) replan(level, v, data, job, req.get(), village.get(), st, now);
		SelfCraftDecision d = st.decision;
		boolean owns = false;
		if (d instanceof SelfCraftDecision.KeepFetching) owns = fetchTick(level, v, data, job, req.get(), st, now);
		else if (d instanceof SelfCraftDecision.Continue) owns = craftTick(level, v, data, job, st, now);
		if (owns && !st.owning) {
			st.owning = true;
			takeOver(level, v, job, st);
		} else if (!owns && st.owning) {
			endOwning(v, st);
		}
		return owns;
	}

	/** The job's behaviour ended (night, panic, raid, fired): the walk is released, a fetch is undone, the progress saved. */
	public static void stop(ServerLevel level, Villager v) {
		SelfCraftRuntime rt = SelfCraftRuntime.get(level.getServer());
		SelfCraftRuntime.State st = rt.peek(v.getUUID());
		if (st == null || st.suppressStop) return;
		st.walk.release(v);
		if (st.fetching != null) abortFetch(level.getServer(), v.getUUID(), st);
		saveProgress(level.getServer(), v.getUUID(), st);
		if (st.owning) endOwning(v, st);
		st.decision = null;
		st.lastPlan = Long.MIN_VALUE;
	}

	/** The system's cleanup of a fetch that stopped ticking. */
	static void releaseStale(MinecraftServer server, UUID worker, SelfCraftRuntime.State st) {
		abortFetch(server, worker, st);
		st.decision = null;
		st.lastPlan = Long.MIN_VALUE;
	}

	/** The worker was fired or died: its reservations and orders go; a committed record is refunded by the system's cancel check. */
	public static void forget(MinecraftServer server, UUID worker) {
		SelfCraftRuntime rt = SelfCraftRuntime.get(server);
		SelfCraftRuntime.State st = rt.peek(worker);
		if (st != null && st.fetching != null) abortFetch(server, worker, st);
		rt.workers.remove(worker);
		WorkerStatus s = WorkerStatuses.get(worker);
		if (s == WorkerStatus.MAKING_STONE_TOOL || s == WorkerStatus.MAKING_WOODEN_TOOL) WorkerStatuses.set(worker, null);
	}

	private static void takeOver(ServerLevel level, Villager v, JobType job, SelfCraftRuntime.State st) {
		st.suppressStop = true;
		try {
			switch (job) {
				case LUMBERJACK -> dev.villageheart.worker.lumber.LumberJob.stop(level, v);
				case MINER -> dev.villageheart.worker.mine.MinerJob.stop(level, v);
				case HUNTER -> dev.villageheart.worker.hunt.HunterJob.stop(level, v);
				case FARMER -> dev.villageheart.worker.farm.FarmJob.stop(level, v);
				default -> { }
			}
		} finally {
			st.suppressStop = false;
		}
	}

	private static void endOwning(Villager v, SelfCraftRuntime.State st) {
		st.owning = false;
		st.walk.release(v);
		clearMaking(v.getUUID());
	}

	private static void clearMaking(UUID id) {
		WorkerStatus s = WorkerStatuses.get(id);
		if (s == WorkerStatus.MAKING_STONE_TOOL || s == WorkerStatus.MAKING_WOODEN_TOOL) WorkerStatuses.set(id, null);
	}

	// ---- planning ----------------------------------------------------------------------------------

	private static boolean atBoundary(ServerLevel level, Villager v, JobType job) {
		return switch (job) {
			case LUMBERJACK -> dev.villageheart.worker.lumber.LumberJob.atBoundary(level, v);
			case MINER -> !AwayTrips.isAway(v) && !dev.villageheart.worker.mine.MinerKeep.carriesNonFood(v) && AwayTrips.state(v).pending().isEmpty();
			case HUNTER -> !AwayTrips.isAway(v) && !dev.villageheart.worker.hunt.HuntGear.carriesYield(v) && AwayTrips.state(v).pending().isEmpty();
			case FARMER -> true;
			default -> false;
		};
	}

	private static SlotPresence presenceOf(MinecraftServer server, VillageState village, UUID worker, HeartLocation building, JobType job,
			CampDemand census, OrderKey key) {
		if (!ToolCensus.registered(village, building)) return SlotPresence.BUILDING_UNREGISTERED;
		Optional<WorkerRecord> rec = village.worker(worker);
		if (rec.isEmpty() || !rec.get().building().equals(building) || !rec.get().jobId().equals(job.id())) return SlotPresence.HIRED_DROPPED;
		if (census.isUnknown()) return SlotPresence.UNKNOWN;
		List<OpenOrder> open = GearOrders.openOrders(village, building, key.need());
		return ToolDemand.presence(census, open).getOrDefault(key, SlotPresence.COVERED);
	}

	private static void replan(ServerLevel level, Villager v, WorkerData data, JobType job, ToolRequirement req, VillageState village,
			SelfCraftRuntime.State st, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		st.lastPlan = now;
		st.block = null;
		HeartLocation building = data.building();
		GearNeed need = GearNeed.of(req.kind());
		Optional<SelfCraft> saved = village.selfCraft(me);
		Optional<PendingFetch> fetching = Optional.ofNullable(st.fetching);
		long dayTime = Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS);
		boolean hasOrder = saved.isPresent() || fetching.isPresent();
		if (!hasOrder && now < st.retryAt) {
			st.decision = SelfCraftDecision.NONE;
			return;
		}
		if (saved.isPresent() && GearOrders.of(saved.get().craft().key()).isEmpty()) {
			GearOrders.claim(saved.get().craft().key(), me, now, true); // self-heal after a restart
		}
		CampDemand census = ToolCensus.of(server, village, building, job, GearOrders.crafting(), 0);
		WorkerToolState own = census.stateOf(me);
		Optional<OrderKey> myKey = saved.map(s -> s.craft().key()).or(() -> fetching.map(PendingFetch::key));
		SlotPresence presence = myKey.map(k -> presenceOf(server, village, me, building, job, census, k)).orElse(SlotPresence.HELD);
		Set<OrderKey> openKeys = new HashSet<>();
		for (GearOrders.Order o : GearOrders.openOf(building, need)) if (!o.holder().equals(me)) openKeys.add(o.key());
		Optional<OrderKey> freeSlot = hasOrder ? Optional.empty() : ToolDemand.slotFor(me, census, openKeys);
		SlotState slotState = census.isUnknown() ? SlotState.UNKNOWN : own == WorkerToolState.WORN ? SlotState.WORN : SlotState.MISSING;
		boolean bonus = req.mode() == ToolRequirement.Mode.BONUS;
		boolean boundary = (saved.isPresent() || freeSlot.isPresent() || fetching.isPresent()) && atBoundary(level, v, job);
		boolean idle = !bonus || !dev.villageheart.worker.farm.FarmJob.hasWork(level, v);
		boolean smith = SmithService.servesNow(server, village);
		if (freeSlot.isPresent() && !hasOrder) st.slotOpenTicks += PLAN_INTERVAL; else st.slotOpenTicks = 0;

		boolean wantsStart = !hasOrder && freeSlot.isPresent() && (own == WorkerToolState.MISSING || own == WorkerToolState.WORN) && boundary && idle;
		boolean space = true;
		boolean stored = false;
		Optional<CraftPlan> plan = Optional.empty();
		if (wantsStart) {
			space = campHasSpace(server, building);
			if (space) {
				stored = storedToolExists(level, v, need, job);
				if (!stored) plan = TierChoice.selfMade(need, MaterialCensus.of(server, me));
			}
		}
		SelfCraftFacts f = new SelfCraftFacts(own, freeSlot, slotState, req.mode(), saved, Optional.ofNullable(st.cancelReason), fetching, presence,
				boundary, idle, smith, st.slotOpenTicks, stored, space, plan, dayTime);
		SelfCraftDecision d = SelfCraftPlanner.decide(f);
		st.decision = d;
		if (d instanceof SelfCraftDecision.Blocked b) st.block = b.reason();
		if (d instanceof SelfCraftDecision.Start s) {
			beginFetch(level, v, job, st, freeSlot.orElseThrow(), s.plan(), false, now);
		} else if (d instanceof SelfCraftDecision.FetchStored) {
			CraftPlan stub = new CraftPlan(ToolRecipes.of(need, CraftTier.WOODEN), List.of(), 0, 0, List.of());
			beginFetch(level, v, job, st, freeSlot.orElseThrow(), stub, true, now);
		} else if (d instanceof SelfCraftDecision.AbortFetch) {
			abortFetch(server, me, st);
			st.retryAt = now + 200;
			st.decision = SelfCraftDecision.NONE;
		} else if (d instanceof SelfCraftDecision.Cancel c) {
			saved.ifPresent(sc -> cancel(server, sc, job, st));
			st.decision = SelfCraftDecision.NONE;
		}
		if (!(st.decision instanceof SelfCraftDecision.KeepFetching) && !(st.decision instanceof SelfCraftDecision.Continue)) clearMaking(me);
	}

	private static boolean campHasSpace(MinecraftServer server, HeartLocation building) {
		Optional<List<CampContainers.Chest>> chests = CampContainers.of(server, building);
		if (chests.isEmpty()) return false;
		for (CampContainers.Chest c : chests.get()) {
			for (int i = 0; i < c.container().getContainerSize(); i++) if (c.container().getItem(i).isEmpty()) return true;
		}
		return false;
	}

	private static boolean storedToolExists(ServerLevel level, Villager v, GearNeed need, JobType job) {
		return Stockpiles.nearestHolding(level, v.blockPosition(), k -> storedKey(k, need, job)).isPresent();
	}

	private static boolean storedKey(StackKey k, GearNeed need, JobType job) { return ToolStacks.storedUsable(k.toStack(1), need, job); }

	// ---- the fetch (before the commit) --------------------------------------------------------------

	private static void beginFetch(ServerLevel level, Villager v, JobType job, SelfCraftRuntime.State st, OrderKey key, CraftPlan plan,
			boolean storedTool, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		java.util.function.Predicate<StackKey> match;
		if (storedTool) {
			GearNeed need = key.need();
			match = k -> storedKey(k, need, job);
		} else {
			String first = plan.take().get(0).id();
			match = k -> k.patch().isEmpty() && k.id().equals(first);
		}
		Optional<BlockPos> target = Stockpiles.nearestHolding(level, v.blockPosition(), match);
		if (target.isEmpty()) {
			st.retryAt = now + 200;
			st.decision = SelfCraftDecision.NONE;
			return;
		}
		var res = Stockpiles.reservations(server);
		for (ItemCount ic : plan.take()) res.set(me, StackKey.of(item(ic.id())), ic.count());
		GearOrders.claim(key, me, now, false);
		st.fetching = new PendingFetch(key, plan, storedTool, now);
		st.storage = target.get();
		st.fetchTick = now;
		st.walk.clear();
		st.walk.begin(target.get(), CHEST_REACH, now);
		if (!storedTool) setMaking(me, plan.recipe().tier());
		st.decision = SelfCraftDecision.KEEP_FETCHING;
	}

	private static void setMaking(UUID id, CraftTier tier) {
		WorkerStatuses.set(id, tier == CraftTier.STONE ? WorkerStatus.MAKING_STONE_TOOL : WorkerStatus.MAKING_WOODEN_TOOL);
	}

	/** Nothing has moved yet: the reservations and the order go. */
	private static void abortFetch(MinecraftServer server, UUID me, SelfCraftRuntime.State st) {
		PendingFetch pf = st.fetching;
		st.fetching = null;
		st.storage = null;
		Stockpiles.reservations(server).release(me);
		if (pf != null) {
			Optional<GearOrders.Order> o = GearOrders.of(pf.key());
			if (o.isPresent() && o.get().holder().equals(me) && !o.get().committed()) GearOrders.release(pf.key());
		}
		clearMaking(me);
	}

	private static boolean fetchTick(ServerLevel level, Villager v, WorkerData data, JobType job, ToolRequirement req, SelfCraftRuntime.State st, long now) {
		PendingFetch pf = st.fetching;
		MinecraftServer server = level.getServer();
		if (pf == null || st.storage == null) {
			st.decision = null;
			return false;
		}
		st.fetchTick = now;
		if (now - pf.startedAt() > Balance.SELF_FETCH_TIMEOUT) {
			abortFetch(server, v.getUUID(), st);
			st.retryAt = now + 200;
			st.decision = SelfCraftDecision.NONE;
			return false;
		}
		if (!pf.storedTool()) setMaking(v.getUUID(), pf.plan().recipe().tier());
		WalkTo.State ws = st.walk.tick(v, now);
		if (ws == WalkTo.State.WALKING) return true;
		if (ws == WalkTo.State.UNREACHABLE) {
			st.walk.clear();
			abortFetch(server, v.getUUID(), st);
			st.retryAt = now + 200;
			st.decision = SelfCraftDecision.NONE;
			return false;
		}
		st.walk.clear();
		commit(level, v, data, job, st, pf, now);
		return st.fetching != null || st.decision instanceof SelfCraftDecision.Continue;
	}

	/** At Storage: re-takes the census once; withdraws exactly the planned inputs into the saved record (the commit), or aborts. */
	private static void commit(ServerLevel level, Villager v, WorkerData data, JobType job, SelfCraftRuntime.State st, PendingFetch pf, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) {
			abortFetch(server, me, st);
			st.decision = SelfCraftDecision.NONE;
			return;
		}
		CampDemand census = ToolCensus.of(server, village.get(), data.building(), job, GearOrders.crafting(), 0);
		SlotPresence presence = presenceOf(server, village.get(), me, data.building(), job, census, pf.key());
		BlockPos at = v.blockPosition();
		if (presence != SlotPresence.HELD) {
			abortFetch(server, me, st);
			st.retryAt = now + 200;
			st.decision = SelfCraftDecision.NONE;
			return;
		}
		if (pf.storedTool()) {
			commitStoredTool(level, v, data, job, st, pf, at, now);
			return;
		}
		List<ItemStack> got = new ArrayList<>();
		boolean short_ = false;
		for (ItemCount ic : pf.plan().take()) {
			StackKey key = StackKey.of(item(ic.id()));
			int left = ic.count();
			while (left > 0) {
				ItemStack s = Stockpiles.withdraw(level, at, key, left, me);
				if (s.isEmpty()) break;
				got.add(s);
				left -= s.getCount();
			}
			if (left > 0) short_ = true;
		}
		if (short_) {
			// the player took items meanwhile: everything goes back, nothing is committed
			for (ItemStack s : got) {
				Stockpiles.deposit(level, at, s);
				if (!s.isEmpty()) v.getInventory().addItem(s);
			}
			abortFetch(server, me, st);
			st.retryAt = now + 40;
			st.decision = SelfCraftDecision.NONE;
			st.lastPlan = Long.MIN_VALUE;
			return;
		}
		List<ItemCount> committed = new ArrayList<>();
		for (ItemStack s : got) addCount(committed, BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount());
		SelfCrafts.commit(server, new SelfCraft(me, new GearCraft(pf.plan().recipe().outputId(), pf.key(), 0, committed), false));
		Stockpiles.reservations(server).release(me);
		st.fetching = null;
		st.storage = null;
		st.progress = 0;
		st.progressRecipe = pf.plan().recipe().outputId();
		st.progressKey = pf.key();
		st.lastSaveTick = now;
		st.lastHitProgress = 0;
		st.decision = SelfCraftDecision.CONTINUE;
	}

	private static void commitStoredTool(ServerLevel level, Villager v, WorkerData data, JobType job, SelfCraftRuntime.State st, PendingFetch pf,
			BlockPos at, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		GearNeed need = pf.key().need();
		ItemStack tool = Stockpiles.withdrawFirst(level, at, k -> storedKey(k, need, job), 1, me);
		boolean placed = false;
		if (!tool.isEmpty()) {
			placed = placeInCamp(server, data.building(), tool);
			if (!placed) {
				Stockpiles.deposit(level, at, tool); // the chest filled up meanwhile: back to Storage, once
				if (!tool.isEmpty()) v.getInventory().addItem(tool);
			} else {
				WorkStats.add(job.id(), "tools_fetched", 1);
			}
		}
		abortFetch(server, me, st);
		st.retryAt = placed ? now : now + 200;
		st.decision = SelfCraftDecision.NONE;
		st.lastPlan = Long.MIN_VALUE;
	}

	private static void addCount(List<ItemCount> list, String id, int n) {
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id().equals(id)) {
				list.set(i, new ItemCount(id, list.get(i).count() + n));
				return;
			}
		}
		list.add(new ItemCount(id, n));
	}

	// ---- the craft ---------------------------------------------------------------------------------

	private static boolean craftTick(ServerLevel level, Villager v, WorkerData data, JobType job, SelfCraftRuntime.State st, long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		Optional<SelfCraft> saved = VillageClaims.current(server).flatMap(vs -> vs.selfCraft(me));
		if (saved.isEmpty()) {
			st.decision = null;
			return false;
		}
		SelfCraft sc = saved.get();
		Optional<ToolRecipe> recipe = ToolRecipes.byOutput(sc.craft().recipeId());
		if (recipe.isEmpty()) {
			SelfCrafts.refund(server, sc);
			st.decision = null;
			return false;
		}
		if (st.progress < 0 || !sc.craft().recipeId().equals(st.progressRecipe) || !sc.craft().key().equals(st.progressKey)) {
			st.progress = sc.craft().progress();
			st.progressRecipe = sc.craft().recipeId();
			st.progressKey = sc.craft().key();
			st.lastSaveTick = now;
			st.lastHitProgress = (long) (st.progress / Balance.SELF_CRAFT_HIT_TICKS);
		}
		setMaking(me, recipe.get().tier());
		HeartLocation jbl = data.jobBlock();
		if (!jbl.dimension().equals(level.dimension().identifier().toString())) return false;
		BlockPos jb = new BlockPos(jbl.x(), jbl.y(), jbl.z());
		if (WalkTo.distance(v, jb) > Balance.JOB_BLOCK_REACH) {
			if (!st.walk.active() || !st.walk.target().equals(jb)) st.walk.begin(jb, Balance.JOB_BLOCK_REACH, now);
			if (st.walk.tick(v, now) == WalkTo.State.UNREACHABLE) {
				st.walk.clear();
				st.decision = SelfCraftDecision.PAUSE;
				st.lastPlan = now; // pause for a plan interval, the job's own tick runs meanwhile
				return false;
			}
			return true;
		}
		st.walk.release(v);
		v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(jb));
		double mult = WorkSpeed.untooled(data.level(), data.needs());
		if (mult > 0) st.progress += mult;
		long hits = (long) (st.progress / Balance.SELF_CRAFT_HIT_TICKS);
		if (hits > st.lastHitProgress) {
			st.lastHitProgress = hits;
			hit(level, v, jb, recipe.get().tier());
		}
		if (now - st.lastSaveTick >= Balance.SELF_CRAFT_SAVE_TICKS) saveProgress(server, me, st);
		if (st.progress >= recipe.get().tier().selfTicks()) {
			finish(level, v, data, job, st, sc, recipe.get(), now);
			return false;
		}
		return true;
	}

	private static void hit(ServerLevel level, Villager v, BlockPos jb, CraftTier tier) {
		v.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, jb, tier == CraftTier.STONE ? SoundEvents.STONE_HIT : SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.6f, 1.0f);
		ItemStack head = new ItemStack(tier == CraftTier.STONE ? Items.COBBLESTONE : Items.OAK_PLANKS);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, head), jb.getX() + 0.5, jb.getY() + 1.05, jb.getZ() + 0.5, 4, 0.2, 0.05, 0.2, 0.05);
	}

	private static void saveProgress(MinecraftServer server, UUID me, SelfCraftRuntime.State st) {
		if (st.progress < 0 || st.progressRecipe == null || st.progressKey == null) return;
		SelfCrafts.progress(server, me, st.progressRecipe, st.progressKey, (int) st.progress);
		st.lastSaveTick = server.overworld().getGameTime();
	}

	private static void finish(ServerLevel level, Villager v, WorkerData data, JobType job, SelfCraftRuntime.State st, SelfCraft sc, ToolRecipe recipe,
			long now) {
		MinecraftServer server = level.getServer();
		UUID me = v.getUUID();
		BlockPos at = v.blockPosition();
		ItemStack tool = new ItemStack(item(recipe.outputId()));
		if (!placeInCamp(server, data.building(), tool)) {
			Stockpiles.deposit(level, at, tool);
			if (!tool.isEmpty()) v.getInventory().addItem(tool);
			WorkStats.add(job.id(), "tools_to_storage", 1);
			st.block = dev.villageheart.core.gear.BlockReason.NO_SPACE;
		}
		for (ItemCount ic : leftoversOf(sc, recipe)) {
			Item it = item(ic.id());
			int left = ic.count();
			while (left > 0) {
				ItemStack s = new ItemStack(it, Math.min(left, it.getDefaultMaxStackSize()));
				left -= s.getCount();
				Stockpiles.deposit(level, at, s);
				if (!s.isEmpty()) v.getInventory().addItem(s);
			}
		}
		WorkStats.add(job.id(), "tools_made." + recipe.tier().id(), 1);
		SelfCrafts.finish(server, me, sc.craft().recipeId(), sc.craft().key());
		st.progress = -1;
		st.progressRecipe = null;
		st.progressKey = null;
		st.decision = null;
		st.lastPlan = Long.MIN_VALUE;
		level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_WORK_TOOLSMITH, SoundSource.NEUTRAL, 0.8f, 1.0f);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.0, v.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
		moveUnassignedWornToStorage(level, v, data, job);
	}

	/** What the committed raw inputs leave after the recipe: the committed stock minus what the recipe consumes. */
	private static List<ItemCount> leftoversOf(SelfCraft sc, ToolRecipe recipe) {
		// Recompute from the plan's arithmetic: committed items are the plan's take; re-derive the leftovers by simulating the crafting
		int planks = 0, sticks = 0;
		String planksId = null;
		List<ItemCount> out = new ArrayList<>();
		int stoneOrPlankHead = recipe.head();
		int logsPlanks = 0;
		for (ItemCount ic : sc.craft().committed()) {
			dev.villageheart.core.gear.MaterialRole role = roleOfId(ic.id());
			if (role == dev.villageheart.core.gear.MaterialRole.STICK) sticks += ic.count();
			else if (role == dev.villageheart.core.gear.MaterialRole.PLANKS) {
				planks += ic.count();
				planksId = ic.id();
			} else if (role == dev.villageheart.core.gear.MaterialRole.LOGS) {
				logsPlanks += ic.count() * ToolRecipes.PLANKS_PER_LOG;
				planksId = dev.villageheart.core.gear.PlankTable.planksOf(ic.id()).orElse(planksId);
			}
		}
		int totalPlanks = planks + logsPlanks;
		int needSticks = recipe.sticks();
		if (sticks < needSticks) {
			int batches = (needSticks + ToolRecipes.STICKS_PER_BATCH - 1) / ToolRecipes.STICKS_PER_BATCH;
			totalPlanks -= batches * ToolRecipes.PLANKS_PER_STICK_BATCH;
			sticks += batches * ToolRecipes.STICKS_PER_BATCH;
		}
		sticks -= needSticks;
		if (recipe.tier() == CraftTier.WOODEN) totalPlanks -= stoneOrPlankHead;
		if (sticks > 0) out.add(new ItemCount(ToolRecipes.STICK, sticks));
		if (totalPlanks > 0 && planksId != null) out.add(new ItemCount(planksId, totalPlanks));
		return out;
	}

	private static dev.villageheart.core.gear.MaterialRole roleOfId(String id) {
		StackKey k = StackKey.of(item(id));
		return ToolStacks.roleOf(k).orElse(null);
	}

	/** First empty slot of the camp's containers; false when there is none (or the chests cannot be read). */
	private static boolean placeInCamp(MinecraftServer server, HeartLocation building, ItemStack tool) {
		Optional<List<CampContainers.Chest>> chests = CampContainers.of(server, building);
		if (chests.isEmpty()) return false;
		for (CampContainers.Chest c : chests.get()) {
			Container cont = c.container();
			for (int i = 0; i < cont.getContainerSize(); i++) {
				if (cont.getItem(i).isEmpty()) {
					cont.setItem(i, tool.copy());
					cont.setChanged();
					tool.setCount(0);
					return true;
				}
			}
		}
		return false;
	}

	/** Worn tools no worker is assigned (a worn wooden pickaxe beside a fresh stone one) go to Storage so camps do not fill up with them. */
	private static void moveUnassignedWornToStorage(ServerLevel level, Villager v, WorkerData data, JobType job) {
		MinecraftServer server = level.getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		Optional<List<CampContainers.Chest>> chests = CampContainers.of(server, data.building());
		if (village.isEmpty() || chests.isEmpty()) return;
		record Ref(Container c, int slot) {}
		List<ToolAllocation.Tool<Ref>> tools = new ArrayList<>();
		for (int ci = 0; ci < chests.get().size(); ci++) {
			Container c = chests.get().get(ci).container();
			for (int si = 0; si < c.getContainerSize(); si++) {
				ItemStack s = c.getItem(si);
				if (!CampTools.isTool(job.tool().orElseThrow().kind(), s)) continue;
				tools.add(new ToolAllocation.Tool<>(new Ref(c, si), CampTools.tierOf(s), ci * 1000 + si, ToolStacks.remaining(s)));
			}
		}
		Set<UUID> crafting = GearOrders.crafting();
		List<UUID> workers = new ArrayList<>();
		for (UUID w : ToolCensus.hired(village.get(), data.building(), job)) if (!crafting.contains(w)) workers.add(w);
		var assigned = ToolAllocation.assign(tools, workers, t -> {
			ItemStack s = t.ref().c().getItem(t.ref().slot());
			return job == JobType.HUNTER ? dev.villageheart.worker.hunt.HuntGear.rank(s) : t.tier().ordinal();
		});
		Set<Ref> keep = new HashSet<>(assigned.values());
		for (ToolAllocation.Tool<Ref> t : tools) {
			if (keep.contains(t.ref())) continue;
			ItemStack s = t.ref().c().getItem(t.ref().slot());
			if (s.isEmpty() || !ToolStacks.worn(s, job)) continue;
			ItemStack moving = s.copy();
			Stockpiles.deposit(level, v.blockPosition(), moving);
			if (moving.isEmpty()) {
				t.ref().c().setItem(t.ref().slot(), ItemStack.EMPTY);
				t.ref().c().setChanged();
				WorkStats.add(job.id(), "tools_to_storage", 1);
			}
		}
	}

	// ---- cancel ------------------------------------------------------------------------------------

	static void cancel(MinecraftServer server, SelfCraft sc, JobType job, SelfCraftRuntime.State st) {
		boolean done = SelfCrafts.refund(server, sc);
		if (done) WorkStats.add(job.id(), "tools_cancelled", 1);
		st.progress = -1;
		st.progressRecipe = null;
		st.progressKey = null;
		st.cancelReason = null;
		st.cancelCount = 0;
		VillageHeart.LOG.info("Self-craft of {} cancelled: {}", sc.worker(), sc.craft().recipeId());
	}

	private static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }
}
