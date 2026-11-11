package dev.villageheart.worker.hunt;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.hunt.HuntApproach;
import dev.villageheart.core.hunt.HuntPace;
import dev.villageheart.core.hunt.HunterPlanner;
import dev.villageheart.core.hunt.HunterPlanner.Action;
import dev.villageheart.core.hunt.HunterPlanner.Decision;
import dev.villageheart.core.hunt.HunterPlanner.PlanInputs;
import dev.villageheart.core.hunt.TripDay;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.mine.AwayCheck;
import dev.villageheart.core.mine.AwayProgress;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.DepositTrip;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayHauls;
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
 * The hunter at the Lodge (M17 plan 3.2 #13): every 20 ticks it asks {@link HunterPlanner} what to do and carries it out: wait at the
 * fletching table, let a deposit trip take the yield, or walk to the ground and vanish into it ({@link AwayTrips#hide}). Out in the
 * ground the hunter is stepped by {@link HunterSystem}. Transient state only; everything that must survive a reload is in the
 * {@link AwayState} attachment.
 */
public final class HunterJob {
	public static final String JOB = "hunter";
	private static final int PLAN_INTERVAL = 20;
	static final boolean TRACE = Boolean.getBoolean("villageheart.hunter.trace");

	private static final Map<UUID, State> STATES = new HashMap<>();

	private HunterJob() {}

	static final class State {
		final WalkTo walk = new WalkTo();
		Decision decision;
		HuntSites.GroundSite ground;
		long lastPlan = Long.MIN_VALUE;
		long unreachableAt = Long.MIN_VALUE;
		long idleBackoffUntil;
		/** Where this hunter was when it started being pushed against another villager. */
		net.minecraft.world.phys.Vec3 jamPos;
		long jamAt = Long.MIN_VALUE;
		/** Until this tick the side-step owns the navigation; {@link #goOut} does not start a new walk. */
		long yieldUntil;
	}

	static void trace(String fmt, Object... args) {
		if (TRACE) VillageHeart.LOG.info("[hunter] " + fmt, args);
	}

	public static void forget(UUID villager) { STATES.remove(villager); }

	public static void clearForTests() { STATES.clear(); }

	private static State state(UUID id) { return STATES.computeIfAbsent(id, k -> new State()); }

	/** Test hook: the hunter's current decision, or null. */
	public static Decision decisionOf(UUID villager) {
		State s = STATES.get(villager);
		return s == null ? null : s.decision;
	}

	/** Test hook: the trips the hunter has completed on the day of {@code dayTime}. */
	public static int doneToday(Villager v, long dayTime) {
		AwayState st = AwayTrips.state(v);
		return TripDay.of(st.doneDay(), st.doneToday(), Math.floorDiv(dayTime, (long) Balance.DAY_TICKS)).done();
	}

	// ---- behaviour entry points ---------------------------------------------------------------------

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.HUNTER) return;
		if (AwayTrips.isAway(v)) return; // hidden: HunterSystem steps it
		if (Workers.tripRunning(v)) return; // a deposit trip owns the walk
		if (dev.villageheart.worker.gear.SelfCraftJob.tick(level, v, now)) return; // making its own sword (M19a)
		State ws = state(v.getUUID());
		AwayHauls.flushPending(v);
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
		CampTools.Result lease = CampTools.lease(server, v, ToolKind.BOW_OR_SWORD, HuntGear::rank);
		AwayCheck.Lease leaseState = leaseState(lease);
		Optional<HuntSites.GroundSite> ground = HuntSites.groundFor(server, data.building());
		AwayState st = AwayTrips.state(v);
		long day = Math.floorDiv(level.getDayTime(), (long) Balance.DAY_TICKS);
		int done = TripDay.of(st.doneDay(), st.doneToday(), day).done();
		double progress = ground.isPresent() && st.progressShaft().equals(Optional.of(ground.get().loc())) ? st.progress().units() : 0;
		double mult = HuntPace.multiplier(lease instanceof CampTools.Held, data.level(), data.needs());
		OptionalLong since = ws.unreachableAt == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(now - ws.unreachableAt);
		double dist = ground.map(g -> WalkTo.distance(v, g.marker())).orElse(0.0);
		PlanInputs in = new PlanInputs(hasSlot, HuntGear.carriesYield(v), !st.pending().isEmpty(), leaseState, ground.isPresent(),
				Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS), progress, mult, data.level(), done, since, dist);
		Decision d = HunterPlanner.decide(in);
		if (ws.decision == null || !ws.decision.equals(d)) trace("{} @{} plan {} (lease {}, ground {}, done {}, dist {})", v.getId(), now, d, leaseState, ground.isPresent(), done, dist);
		ws.decision = d;
		ws.ground = ground.orElse(null);
		UUID id = v.getUUID();
		switch (d.status()) {
			case KEEP -> { }
			case CLEAR -> WorkerStatuses.set(id, null);
			case NEEDS_TOOL -> WorkerStatuses.set(id, WorkerStatus.NEEDS_TOOL);
			case NO_WORK -> WorkerStatuses.set(id, WorkerStatus.NO_WORK);
			case SITE_UNREACHABLE -> WorkerStatuses.set(id, WorkerStatus.SITE_UNREACHABLE);
		}
		// nothing to deliver, nothing to take: a full inventory of kept stuff must not hold a yield back for ever
		if (d.action() == Action.YIELD && !st.pending().isEmpty() && !HuntGear.carriesYield(v) && !DepositTrip.wanted(level, v)) dropPending(level, v, st);
	}

	private static void dropPending(ServerLevel level, Villager v, AwayState st) {
		VillageHeart.LOG.warn("Hunter {} cannot take its yield (inventory full of kept items): dropping it", v.getUUID());
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
			case IDLE_AT_LODGE, WALK_TO_LODGE -> idle(level, v, data, ws, now);
			case GO_OUT -> goOut(level, v, data, ws, now);
		}
	}

	private static void idle(ServerLevel level, Villager v, WorkerData data, State ws, long now) {
		HeartLocation jb = data.jobBlock();
		if (now < ws.idleBackoffUntil || !jb.dimension().equals(level.dimension().identifier().toString())) return;
		BlockPos target = new BlockPos(jb.x(), jb.y(), jb.z());
		if (WalkTo.distance(v, target) <= Balance.JOB_BLOCK_REACH) {
			ws.walk.release(v);
			return;
		}
		if (!ws.walk.active() || !ws.walk.target().equals(target)) ws.walk.begin(target, Balance.JOB_BLOCK_REACH, now);
		if (ws.walk.tick(v, now) == WalkTo.State.UNREACHABLE) {
			trace("{} @{} the walk to the fletching table failed: {}", v.getId(), now, ws.walk.reason());
			ws.walk.clear();
			ws.idleBackoffUntil = now + 100;
		}
	}

	/** Another villager close enough to be what this one is pushing against. Away or not: a walking partner jams the same way. */
	private static Villager blockingVillager(ServerLevel level, Villager v) {
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager o : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(1.0, 0.25, 1.0), x -> x != v && x.isAlive())) {
			double d = o.distanceToSqr(v);
			if (d < bestD) {
				bestD = d;
				best = o;
			}
		}
		return best;
	}

	/** Ticks this hunter has stayed put while {@code blocking} is there. */
	private static long jammedFor(State ws, Villager v, Villager blocking, long now) {
		if (blocking == null) {
			ws.jamPos = null;
			ws.jamAt = Long.MIN_VALUE;
			return 0;
		}
		if (ws.jamPos == null || v.position().distanceTo(ws.jamPos) >= 0.3) {
			ws.jamPos = v.position();
			ws.jamAt = now;
			return 0;
		}
		return now - ws.jamAt;
	}

	/** One block off the line to {@code blocking}, onto air with ground under it. The navigation walks there. */
	private static boolean stepAside(ServerLevel level, Villager v, Villager blocking, State ws, long now) {
		BlockPos here = v.blockPosition();
		BlockPos other = blocking.blockPosition();
		int ax = Integer.signum(here.getX() - other.getX());
		int az = Integer.signum(here.getZ() - other.getZ());
		BlockPos[] opts = {here.offset(az == 0 ? 1 : az, 0, ax == 0 ? 1 : -ax), here.offset(az == 0 ? -1 : -az, 0, ax == 0 ? -1 : ax),
				here.offset(ax == 0 ? 1 : -ax, 0, az == 0 ? 1 : -az), here.offset(1, 0, 0), here.offset(-1, 0, 0), here.offset(0, 0, 1), here.offset(0, 0, -1)};
		for (BlockPos c : opts) {
			if (c.equals(here) || c.equals(other) || !standable(level, c)) continue;
			ws.walk.release(v);
			v.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.5);
			ws.yieldUntil = now + 20;
			ws.jamPos = null;
			ws.jamAt = Long.MIN_VALUE;
			trace("{} @{} jammed against {} at {}: steps aside to {}", v.getId(), now, blocking.getId(), here, c);
			return true;
		}
		return false;
	}

	private static boolean standable(ServerLevel level, BlockPos c) {
		if (level.getChunkSource().getChunkNow(c.getX() >> 4, c.getZ() >> 4) == null) return false;
		if (level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty()) return false;
		if (!level.getBlockState(c).getCollisionShape(level, c).isEmpty()) return false;
		return level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty();
	}

	private static void goOut(ServerLevel level, Villager v, WorkerData data, State ws, long now) {
		if (now < ws.yieldUntil) return; // the side-step is still walking
		HuntSites.GroundSite ground = ws.ground;
		if (ground == null || ground.level() != level) return;
		BlockPos target = ground.marker();
		if (!ws.walk.active() || !ws.walk.target().equals(target)) ws.walk.begin(target, Balance.HUNT_ENTRY_REACH, Balance.HUNT_ENTRY_CLOSEST, now);
		WalkTo.State st = ws.walk.tick(v, now);
		Villager blocking = blockingVillager(level, v);
		double dist = WalkTo.distance(v, target);
		// Touching someone near the marker counts as arrived, whether or not they are already hidden. Farther away, the higher id
		// steps aside after a short push so the stall (200) and the unreachable retry (600) never start. That retry is what made
		// two hunters miss the 450-tick bound.
		if (st == WalkTo.State.WALKING && HuntApproach.choose(dist, Balance.HUNT_ENTRY_CLOSEST, blocking != null, false) == HuntApproach.Choice.ARRIVE) {
			trace("{} @{} blocked near the ground: goes out here", v.getId(), now);
			st = WalkTo.State.ARRIVED;
		} else if (st == WalkTo.State.WALKING && HuntApproach.yieldNow(jammedFor(ws, v, blocking, now), blocking != null,
				blocking != null && !blocking.isNoAi() && blocking.getId() < v.getId(), dist, Balance.HUNT_ENTRY_CLOSEST)) {
			if (stepAside(level, v, blocking, ws, now)) return;
		}
		if (st == WalkTo.State.WALKING) return;
		if (st == WalkTo.State.UNREACHABLE) {
			HuntApproach.Choice choice = HuntApproach.choose(dist, Balance.HUNT_ENTRY_CLOSEST, blocking != null, true);
			if (choice == HuntApproach.Choice.ARRIVE) {
				trace("{} @{} the walk gave up against a villager near the ground: goes out here", v.getId(), now);
				st = WalkTo.State.ARRIVED;
			} else if (choice == HuntApproach.Choice.YIELD) {
				trace("{} @{} the walk gave up against villager {}: not an unreachable ground", v.getId(), now, blocking == null ? "-" : blocking.getId());
				if (blocking != null && stepAside(level, v, blocking, ws, now)) return;
				ws.walk.clear();
				ws.decision = null; // plan again next tick; do not arm the 600-tick retry
				return;
			} else {
				trace("{} @{} the ground at {} is unreachable: {}", v.getId(), now, target, ws.walk.reason());
				ws.walk.clear();
				ws.unreachableAt = now;
				ws.decision = null;
				WorkerStatuses.set(v.getUUID(), WorkerStatus.SITE_UNREACHABLE);
				return;
			}
		}
		AwayState as = AwayTrips.state(v);
		if (!as.progressShaft().equals(Optional.of(ground.loc()))) as = as.withProgress(AwayProgress.ZERO, Optional.of(ground.loc()));
		// the walk may have been long: the trip must still end in the work phase, else back to the Lodge (nothing is reported)
		CampTools.Result lease = CampTools.lease(level.getServer(), v, ToolKind.BOW_OR_SWORD, HuntGear::rank);
		double mult = HuntPace.multiplier(lease instanceof CampTools.Held, data.level(), data.needs());
		if (!(lease instanceof CampTools.Held)) {
			// the weapon is gone or its chest cannot be read right now: no departure, the next plan says why (NEEDS_TOOL, or waits)
			trace("{} @{} arrived at the ground without a readable weapon ({})", v.getId(), now, lease);
			ws.walk.release(v);
			ws.decision = null;
			return;
		}
		if (!HunterPlanner.fitsNow(level.getDayTime(), as.progress().units(), mult)) {
			trace("{} @{} arrived too late for a trip (mult {})", v.getId(), now, mult);
			ws.walk.release(v);
			ws.decision = null;
			return;
		}
		AwayTrips.set(v, as.withUnknownSteps(0));
		AwayTrips.hide(level, v, JOB, ground.loc(), HuntSites.holdCell(level, v, target));
		WorkStats.add(JOB, "trips", 1);
		WorkerStatuses.set(v.getUUID(), null);
		ws.walk.release(v);
		ws.decision = null;
		ws.unreachableAt = Long.MIN_VALUE;
		trace("{} @{} went out to {}", v.getId(), now, ground.loc());
	}
}
