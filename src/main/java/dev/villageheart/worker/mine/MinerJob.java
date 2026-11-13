package dev.villageheart.worker.mine;

import dev.villageheart.VillageHeart;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.ShaftScanner.ShaftDepth;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.mine.AwayCheck;
import dev.villageheart.core.mine.AwayProgress;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.MinerPlanner;
import dev.villageheart.core.mine.MinerPlanner.Action;
import dev.villageheart.core.mine.MinerPlanner.Decision;
import dev.villageheart.core.mine.MinerPlanner.PlanInputs;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.DepositTrip;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The miner on the surface (M16 plan 3.2 #10): every 20 ticks it asks {@link MinerPlanner} what to do and carries it out: wait at the
 * stonecutter, let a deposit trip take the load, or walk to the shaft and go down ({@link AwayTrips#hide}). Underground the miner is
 * stepped by {@link MinerSystem}. Transient state only; everything that must survive a reload is in the {@link AwayState} attachment.
 */
public final class MinerJob {
	public static final String JOB = "miner";
	private static final int PLAN_INTERVAL = 20;
	static final boolean TRACE = Boolean.getBoolean("villageheart.miner.trace");

	private static final Map<UUID, State> STATES = new HashMap<>();

	private MinerJob() {}

	static final class State {
		final WalkTo walk = new WalkTo();
		Decision decision;
		MineSites.ShaftSite shaft;
		long lastPlan = Long.MIN_VALUE;
		long unreachableAt = Long.MIN_VALUE;
		long idleBackoffUntil;
	}

	static void trace(String fmt, Object... args) {
		if (TRACE) VillageHeart.LOG.info("[miner] " + fmt, args);
	}

	public static void forget(UUID villager) { STATES.remove(villager); }

	public static void clearForTests() { STATES.clear(); }

	private static State state(UUID id) { return STATES.computeIfAbsent(id, k -> new State()); }

	/** Test hook: the miner's current decision, or null. */
	public static Decision decisionOf(UUID villager) {
		State s = STATES.get(villager);
		return s == null ? null : s.decision;
	}

	// ---- behaviour entry points ---------------------------------------------------------------------

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.MINER) return;
		if (AwayTrips.isAway(v)) return; // hidden: MinerSystem steps it
		if (Workers.tripRunning(v)) return; // a deposit trip owns the walk
		if (dev.villageheart.worker.gear.SelfCraftJob.tick(level, v, now)) return; // making its own pickaxe (M19a)
		State ws = state(v.getUUID());
		MinerHauls.flushPending(v);
		if (ws.decision == null || now - ws.lastPlan >= PLAN_INTERVAL) plan(level, v, data, ws, now);
		run(level, v, data, ws, now);
	}

	public static void stop(ServerLevel level, Villager v) {
		dev.villageheart.worker.gear.SelfCraftJob.stop(level, v);
		State ws = STATES.get(v.getUUID());
		if (ws == null) return;
		ws.walk.release(v);
		ws.decision = null;
	}

	// ---- planning -----------------------------------------------------------------------------------

	static AwayCheck.Lease leaseState(CampTools.Result r) {
		return r instanceof CampTools.Held ? AwayCheck.Lease.HELD : r == CampTools.Missing.NONE ? AwayCheck.Lease.NONE : AwayCheck.Lease.UNKNOWN;
	}

	private static void plan(ServerLevel level, Villager v, WorkerData data, State ws, long now) {
		ws.lastPlan = now;
		var server = level.getServer();
		Optional<WorkerRecord> rec = VillageClaims.current(server).flatMap(s -> s.worker(v.getUUID()));
		if (rec.isEmpty()) return; // a worker made without a record (framework tests): nothing to plan
		boolean hasSlot = Workers.status(server, rec.get()) != WorkerStatus.WORKPLACE_INVALID;
		AwayCheck.Lease lease = leaseState(CampTools.lease(server, v, ToolKind.PICKAXE));
		Optional<MineSites.ShaftSite> shaft = MineSites.shaftFor(server, data.building());
		Optional<ShaftDepth> depth = shaft.flatMap(s -> Buildings.shaftDepth(server, s.loc()));
		AwayState st = AwayTrips.state(v);
		OptionalLong since = ws.unreachableAt == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(now - ws.unreachableAt);
		double dist = shaft.map(s -> WalkTo.distance(v, s.marker())).orElse(0.0);
		PlanInputs in = new PlanInputs(hasSlot, MinerKeep.carriesNonFood(v), !st.pending().isEmpty(), lease, shaft.isPresent(), depth.isPresent(),
				Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS), since, dist);
		Decision d = MinerPlanner.decide(in);
		if (ws.decision == null || !ws.decision.equals(d)) trace("{} @{} plan {} (lease {}, shaft {}, depth {}, dist {})", v.getId(), now, d, lease, shaft.isPresent(), depth.isPresent(), dist);
		ws.decision = d;
		ws.shaft = shaft.orElse(null);
		UUID id = v.getUUID();
		switch (d.status()) {
			case KEEP -> { }
			case CLEAR -> WorkerStatuses.set(id, null);
			case NEEDS_TOOL -> WorkerStatuses.set(id, WorkerStatus.NEEDS_TOOL);
			case NO_WORK -> WorkerStatuses.set(id, WorkerStatus.NO_WORK);
			case SITE_UNREACHABLE -> WorkerStatuses.set(id, WorkerStatus.SITE_UNREACHABLE);
		}
		// nothing to deliver, nothing to take: a full inventory of kept stuff must not hold a haul back for ever
		if (d.action() == Action.YIELD && !st.pending().isEmpty() && !MinerKeep.carriesNonFood(v) && !DepositTrip.wanted(level, v)) {
			dropPending(level, v, st);
		}
	}

	private static void dropPending(ServerLevel level, Villager v, AwayState st) {
		VillageHeart.LOG.warn("Miner {} cannot take its haul (inventory full of kept items): dropping it", v.getUUID());
		for (Map.Entry<String, Integer> e : st.pending().entrySet()) {
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.getKey()));
			int left = e.getValue();
			while (left > 0) {
				ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
				left -= stack.getCount();
				v.spawnAtLocation(level, stack);
			}
		}
		AwayTrips.set(v, st.withPending(new LinkedHashMap<>()));
	}

	// ---- acting -------------------------------------------------------------------------------------

	private static void run(ServerLevel level, Villager v, WorkerData data, State ws, long now) {
		Decision d = ws.decision;
		if (d == null) return;
		switch (d.action()) {
			case YIELD -> ws.walk.release(v);
			case IDLE_AT_MINE, WALK_TO_MINE -> idle(level, v, data, ws, now);
			case GO_DOWN -> goDown(level, v, ws, now);
		}
	}

	private static void idle(ServerLevel level, Villager v, WorkerData data, State ws, long now) {
		HeartLocation jb = data.jobBlock();
		if (now < ws.idleBackoffUntil || !jb.dimension().equals(level.dimension().identifier().toString())) return;
		BlockPos target = new BlockPos(jb.x(), jb.y(), jb.z());
		if (dev.villageheart.worker.job.WorkstationStand.at(v, target)) {
			ws.walk.release(v);
			return;
		}
		BlockPos spot = dev.villageheart.worker.job.WorkstationStand.spot(level, target, v.position());
		if (!ws.walk.active() || !ws.walk.target().equals(spot)) ws.walk.begin(spot, spot.equals(target) ? Balance.JOB_BLOCK_REACH : dev.villageheart.worker.job.WorkstationStand.REACH, now);
		if (ws.walk.tick(v, now) == WalkTo.State.UNREACHABLE) {
			ws.walk.clear();
			ws.idleBackoffUntil = now + 100;
		}
	}

	private static void goDown(ServerLevel level, Villager v, State ws, long now) {
		MineSites.ShaftSite shaft = ws.shaft;
		if (shaft == null || shaft.level() != level) return;
		BlockPos target = shaft.marker();
		if (!ws.walk.active() || !ws.walk.target().equals(target)) ws.walk.begin(target, Balance.SHAFT_ENTRY_REACH, Balance.SHAFT_ENTRY_CLOSEST, now);
		WalkTo.State st = ws.walk.tick(v, now);
		if (st == WalkTo.State.WALKING) return;
		if (st == WalkTo.State.UNREACHABLE) {
			trace("{} @{} the shaft at {} is unreachable: {}", v.getId(), now, target, ws.walk.reason());
			ws.walk.clear();
			ws.unreachableAt = now;
			ws.decision = null;
			WorkerStatuses.set(v.getUUID(), WorkerStatus.SITE_UNREACHABLE);
			return;
		}
		Optional<ShaftDepth> depth = Buildings.shaftDepth(level.getServer(), shaft.loc());
		if (depth.isEmpty()) {
			ws.decision = null;
			return;
		}
		AwayState as = AwayTrips.state(v);
		if (!as.progressShaft().equals(Optional.of(shaft.loc()))) as = as.withProgress(AwayProgress.ZERO, Optional.of(shaft.loc()));
		AwayTrips.set(v, as.withUnknownSteps(0));
		AwayTrips.hide(level, v, JOB, shaft.loc(), MineSites.holdSpot(level, target, depth.get()));
		WorkStats.add(JOB, "descents", 1);
		WorkerStatuses.set(v.getUUID(), null);
		ws.walk.release(v);
		ws.decision = null;
		ws.unreachableAt = Long.MIN_VALUE;
		trace("{} @{} went down {}", v.getId(), now, shaft.loc());
	}
}
