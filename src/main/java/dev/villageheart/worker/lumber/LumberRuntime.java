package dev.villageheart.worker.lumber;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.lumber.LumberjackPlanner;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.TreeFinder;
import dev.villageheart.core.tree.TreeScan;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WalkTo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The lumberjacks' transient state (nothing here is saved, M9 section 3.6): which worker is felling which tree, the tree search per camp,
 * the trees given up, and each worker's running task. Dropped when the server or the claim changes. Server thread only.
 */
public final class LumberRuntime {
	private static LumberRuntime current;

	final MinecraftServer server;
	final int epoch;
	/** Key base cell of a tree task or tree -> the worker felling it. */
	final Map<HeartLocation, UUID> claims = new HashMap<>();
	final Map<UUID, WorkerState> workers = new HashMap<>();
	final Map<HeartLocation, CampState> camps = new HashMap<>();
	/** A FELLING task that could not be resumed yet (unloaded cell): not tried again before this game time. */
	final Map<HeartLocation, Long> taskRetryAt = new HashMap<>();
	/** Dropped items given up on (not reachable): item entity -> until. */
	final Map<UUID, Long> itemBlacklist = new HashMap<>();

	private LumberRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	public static LumberRuntime get(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		LumberRuntime rt = current;
		if (rt == null || rt.server != server || rt.epoch != epoch) current = rt = new LumberRuntime(server, epoch);
		return rt;
	}

	/** Tests: forget every claim, search and blacklist, as after a restart. */
	public static void resetForTests() { current = null; }

	/** The workers felling a tree right now: key base cell -> worker. A copy. */
	public Map<HeartLocation, UUID> claims() { return new HashMap<>(claims); }

	/** Test hook: the camp's blacklist (base cell -> until). */
	public Map<HeartLocation, Long> blacklist(HeartLocation camp) {
		CampState c = camps.get(camp);
		return c == null ? Map.of() : new HashMap<>(c.blacklist);
	}

	/** One line about a worker's running task, for test failure messages and logs. */
	public String describe(UUID villager, net.minecraft.world.entity.npc.villager.Villager v) {
		WorkerState ws = workers.get(villager);
		if (ws == null) return "no state";
		return "action=" + ws.action + " decision=" + (ws.decision == null ? null : ws.decision.action() + "/" + ws.decision.status())
				+ " tree=" + (ws.tree == null ? "none" : ws.remaining.size() + "/" + ws.tree.fellOrder().size() + " left") + " key=" + ws.key
				+ " walk=" + (ws.walk.active() ? ws.walk.target() : "-") + " lease=" + (ws.lease != null) + " needsPlan=" + ws.needsPlan
				+ " pos=" + v.blockPosition() + " lastTick=" + ws.lastTick + " status=" + dev.villageheart.worker.job.WorkerStatuses.get(villager)
				+ " backoff=" + ws.idleBackoffUntil + " giveUps=" + giveUps;
	}

	/** Every "cannot reach this tree" verdict so far (tree base and why), so a test can insist that open trees never get one. */
	final java.util.List<String> giveUps = new ArrayList<>();

	public java.util.List<String> giveUps() { return new ArrayList<>(giveUps); }

	WorkerState state(UUID villager) { return workers.computeIfAbsent(villager, k -> new WorkerState()); }

	CampState camp(HeartLocation building) { return camps.computeIfAbsent(building, k -> new CampState()); }

	/** True when a claim of another worker is live: that worker ticked within the last 100 ticks. */
	boolean claimedByOther(HeartLocation key, UUID me, long now) {
		UUID owner = claims.get(key);
		if (owner == null || owner.equals(me)) return false;
		WorkerState ws = workers.get(owner);
		return ws != null && now - ws.lastTick <= 100;
	}

	/** Per camp: the tree search, the trees it found, the trees given up. */
	static final class CampState {
		TreeFinder finder;
		long finderCreatedAt;
		long sweepDoneAt;
		boolean exhausted;
		boolean exhaustedUnknown;
		final ArrayDeque<TreeScan.Tree> found = new ArrayDeque<>();
		final Map<HeartLocation, Long> blacklist = new HashMap<>();

		boolean blacklisted(HeartLocation base, long now) {
			Long until = blacklist.get(base);
			if (until == null) return false;
			if (now >= until) {
				blacklist.remove(base);
				return false;
			}
			return true;
		}

		void giveUp(HeartLocation base, long now) { blacklist.put(base, now + Balance.TREE_BLACKLIST_TICKS); }
	}

	/** One worker's running task. */
	static final class WorkerState {
		long lastTick;
		long lastPlan = Long.MIN_VALUE / 2;
		boolean needsPlan = true;
		LumberjackPlanner.Decision decision;
		LumberjackPlanner.Action action = LumberjackPlanner.Action.IDLE_AT_CAMP;
		long idleBackoffUntil;
		/** The point an idle worker is walking to: its job block, or the end of a leg of the way back when it is far from it. */
		BlockPos idleGoal;
		final WalkTo walk = new WalkTo();
		final WorkClock clock = new WorkClock(Balance.BASE_TICKS_PER_LOG);
		@Nullable HeartLocation clockKey;
		CampTools.Held lease;

		// the tree being felled
		@Nullable TreeScan.Tree tree;
		@Nullable HeartLocation key;
		ArrayDeque<Pos> remaining = new ArrayDeque<>();
		@Nullable BlockPos crackPos;
		int crackStage = -1;

		// replanting
		@Nullable ReplantPlan replant;
		// collecting
		@Nullable UUID item;
		long itemSince;

		void clearTree() {
			tree = null;
			key = null;
			remaining = new ArrayDeque<>();
		}
	}

	/** Where the next sapling goes and where it comes from. */
	record ReplantPlan(HeartLocation taskKey, BlockPos cell, String itemId, boolean fromChest, BlockPos chest) {}

	/** Skip predicate for the finder: trees claimed, given up, or already a task. */
	Predicate<Pos> skipFor(HeartLocation building, String dim, long now, UUID me) {
		CampState camp = camp(building);
		List<HeartLocation> taskCells = new ArrayList<>();
		VillageClaims.current(server).ifPresent(v -> {
			for (TreeTask t : v.treeTasks()) taskCells.addAll(t.base());
		});
		return p -> {
			HeartLocation loc = new HeartLocation(dim, p.x(), p.y(), p.z());
			return taskCells.contains(loc) || camp.blacklisted(loc, now) || claimedByOther(loc, me, now);
		};
	}

	/** Used by tests: the states of all workers (read-only view of the keys). */
	List<UUID> workerIds() { return Collections.unmodifiableList(new ArrayList<>(workers.keySet())); }
}
