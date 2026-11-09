package dev.villageheart.worker.cook;

import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.Balance;
import dev.villageheart.core.cook.CookPlanner.Batch;
import dev.villageheart.stock.StackKey;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.job.WalkTo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The cooks' transient state (nothing here is saved, M13 plan 3.4): each cook's running task, batch, work-clock remainder and fetch, and the
 * log of finished units that tests read. Dropped when the server or the claim changes: the inventory is the state, so the planner picks the
 * work up again. Server thread only.
 */
public final class CookRuntime {
	private static CookRuntime current;

	/** What a cook is doing. */
	enum Action { IDLE, FETCH, PREP, COOK }

	/** One finished output item (tests). */
	public record UnitRecord(UUID cook, long tick, BlockPos at, String output) {}

	/** One cook's state. */
	static final class CookState {
		Action action = Action.IDLE;
		@Nullable Batch batch;
		boolean returning;
		boolean needsPlan = true;
		long lastPlan = Long.MIN_VALUE;
		long lastTick;
		long lastValid;
		boolean valid = true;
		final WorkClock clock = new WorkClock(Balance.COOK_TICKS_PER_ITEM);
		final WalkTo walk = new WalkTo();
		// fetch
		final Map<StackKey, Integer> wanted = new LinkedHashMap<>();
		int stops;
		long fetchDeadline;
		@Nullable BlockPos stop;
		// prep (CRAFT batches)
		boolean prepDone;
		long prepStart = -1;
		long prepAt = -1;
		@Nullable BlockPos table;
		// the smoker this cook has lit, and the last tick it worked there
		@Nullable BlockPos litAt;
		long lastCookTick = Long.MIN_VALUE;
		long retryAt;
	}

	final MinecraftServer server;
	final int epoch;
	final Map<UUID, CookState> workers = new HashMap<>();
	private final List<UnitRecord> unitLog = new ArrayList<>();

	private CookRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	public static CookRuntime get(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		CookRuntime rt = current;
		if (rt == null || rt.server != server || rt.epoch != epoch) current = rt = new CookRuntime(server, epoch);
		return rt;
	}

	/** Tests: forget every task, as after a restart. */
	public static void resetForTests() { current = null; }

	CookState state(UUID cook) { return workers.computeIfAbsent(cook, k -> new CookState()); }

	void log(UnitRecord u) {
		if (unitLog.size() > 4000) unitLog.subList(0, 2000).clear();
		unitLog.add(u);
	}

	/** Finished units so far, oldest first (a copy). */
	public List<UnitRecord> unitLog() { return List.copyOf(unitLog); }

	/** True when a cook worked at this smoker within the last 40 ticks (the Kitchen sweep leaves its light on). */
	public boolean cooking(BlockPos smoker, long now) {
		for (CookState st : workers.values()) {
			if (st.litAt != null && st.litAt.equals(smoker) && now - st.lastCookTick <= 40) return true;
		}
		return false;
	}
}
