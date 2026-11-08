package dev.villageheart.worker.farm;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.farm.FarmerPlanner;
import dev.villageheart.core.farm.FieldIndex;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WalkTo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The farmers' transient state (nothing here is saved, M10 plan 3.2 #13): the field index of every Farmhouse composter, which worker works
 * which cell, the composter tenths, each worker's running task and the log of finished units. Dropped when the server or the claim changes.
 * Server thread only.
 */
public final class FarmRuntime {
	private static FarmRuntime current;
	private static final int LOG_CAP = 8192;

	final MinecraftServer server;
	final int epoch;
	/** Crop cell -> the worker that works it. */
	final Map<Pos, UUID> claims = new HashMap<>();
	final Map<UUID, WorkerState> workers = new HashMap<>();
	/** Job block (composter) -> its field index. */
	final Map<HeartLocation, FieldIndex> indexes = new LinkedHashMap<>();
	/** Composter -> tenths of a level left over by the deterministic composting rule. */
	final Map<HeartLocation, Integer> tenths = new HashMap<>();
	private final Map<HeartLocation, Long> indexUsedAt = new HashMap<>();
	/** Dropped items given up on (not reachable): item entity -> until. */
	final Map<UUID, Long> itemBlacklist = new HashMap<>();
	/** Cells that could not be reached: cell -> until. */
	final Map<Pos, Long> cellBlacklist = new HashMap<>();
	final List<String> giveUps = new ArrayList<>();
	private final List<Unit> log = new ArrayList<>();
	private long budgetTick = Long.MIN_VALUE;
	private int rr;

	private FarmRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	public static FarmRuntime get(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		FarmRuntime rt = current;
		if (rt == null || rt.server != server || rt.epoch != epoch) current = rt = new FarmRuntime(server, epoch);
		return rt;
	}

	/** Tests: forget every claim, index and log, as after a restart. */
	public static void resetForTests() { current = null; }

	/** One finished unit of work: the world changed at {@code cell} (HARVEST, PLANT, BONEMEAL, COMPOST, TAKE_BONE_MEAL, FETCH, COLLECT). */
	public record Unit(long tick, UUID worker, String unit, BlockPos cell, double x, double z) {}

	void log(long tick, net.minecraft.world.entity.npc.villager.Villager worker, String unit, BlockPos cell) {
		if (log.size() >= LOG_CAP) log.remove(0);
		log.add(new Unit(tick, worker.getUUID(), unit, cell.immutable(), worker.getX(), worker.getZ()));
	}

	/** The finished units so far (a copy; at most the last 8192). */
	public List<Unit> unitLog() { return new ArrayList<>(log); }

	/** True while the item entity is given up on (test failure messages). */
	public boolean itemBlacklisted(UUID item, long now) { return itemBlacklist.getOrDefault(item, 0L) > now; }

	/** Every "cannot reach this cell" verdict so far. */
	public List<String> giveUps() { return new ArrayList<>(giveUps); }

	/** The cells being worked right now: cell -> worker. */
	public Map<Pos, UUID> claims() { return new HashMap<>(claims); }

	/** Tests: the farmer plans on its next tick. */
	public void forcePlanForTest(UUID villager) {
		WorkerState ws = workers.get(villager);
		if (ws != null) ws.needsPlan = true;
	}

	WorkerState state(UUID villager) { return workers.computeIfAbsent(villager, k -> new WorkerState()); }

	@Nullable
	public FieldIndex index(HeartLocation jobBlock) { return indexes.get(jobBlock); }

	/** The index of this composter (created on first use); marks it as used now so {@link #pump} keeps it fresh. */
	FieldIndex indexFor(HeartLocation jobBlock, long now) {
		indexUsedAt.put(jobBlock, now);
		return indexes.computeIfAbsent(jobBlock, k -> new FieldIndex(new Pos(k.x(), k.y(), k.z())));
	}

	/** True when a claim of another worker is live: that worker ticked within the last 100 ticks. */
	boolean claimedByOther(Pos cell, UUID me, long now) {
		UUID owner = claims.get(cell);
		if (owner == null || owner.equals(me)) return false;
		WorkerState ws = workers.get(owner);
		return ws != null && now - ws.lastTick <= 100;
	}

	void release(UUID me, WorkerState ws) {
		if (ws.cell != null && me.equals(claims.get(ws.cell))) claims.remove(ws.cell);
		ws.cell = null;
	}

	/**
	 * The server-wide index budget, once per tick (called by whichever farmer ticks first): sweeps first (incomplete indexes, round-robin),
	 * then refreshes; an index whose sweep is older than {@link Balance#FIELD_INDEX_TTL} starts over.
	 */
	void pump(ServerLevel level, long now) {
		if (budgetTick == now) return;
		budgetTick = now;
		indexes.keySet().removeIf(k -> now - indexUsedAt.getOrDefault(k, Long.MIN_VALUE / 2) > 600); // no farmer there any more
		if (indexes.isEmpty()) return;
		LevelFieldView view = new LevelFieldView(level);
		List<FieldIndex> all = new ArrayList<>(indexes.values());
		int sweepLeft = Balance.FIELD_SWEEP_READS_PER_TICK;
		int refreshLeft = Balance.FIELD_REFRESH_READS_PER_TICK;
		rr++;
		for (int k = 0; k < all.size(); k++) {
			FieldIndex idx = all.get((rr + k) % all.size());
			if (idx.sweepComplete() && now - idx.sweepCompletedAt() >= Balance.FIELD_INDEX_TTL) idx.restartSweep();
			if (!idx.sweepComplete() && sweepLeft > 0) sweepLeft -= idx.sweep(view, sweepLeft, now);
			if (refreshLeft > 0) refreshLeft -= idx.refresh(view, refreshLeft);
		}
	}

	/** What a worker is doing. */
	static final class WorkerState {
		long lastTick;
		long lastPlan = Long.MIN_VALUE / 2;
		boolean needsPlan = true;
		FarmerPlanner.Decision decision;
		FarmerPlanner.Action action = FarmerPlanner.Action.IDLE_AT_FARMHOUSE;
		long idleBackoffUntil;
		final WalkTo walk = new WalkTo();
		WorkClock clock = new WorkClock(1);
		@Nullable Pos clockKey;
		/** The hoe in the Farmhouse chest, if any, taken at the start of the unit. */
		@Nullable CampTools.Held lease;
		boolean leaseTaken;

		@Nullable Pos cell;
		@Nullable CropKind plantKind;
		/** FETCH: the seed item to take and how many; the chest to walk to. */
		@Nullable String fetchItem;
		int fetchCount;
		@Nullable BlockPos chest;
		// collecting
		@Nullable UUID item;
		long itemSince;

		void resetUnit() {
			clockKey = null;
			lease = null;
			leaseTaken = false;
		}
	}
}
