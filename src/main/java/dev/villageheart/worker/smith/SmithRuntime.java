package dev.villageheart.worker.smith;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.GearOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.smith.SmithBatch;
import dev.villageheart.core.smith.SmithDecision;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.world.VillageClaims;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Per-server smith state. Not saved: a craft's progress lives on the village, and the morning reads it back. */
public final class SmithRuntime {
	private static SmithRuntime current;

	public enum Mode {
		IDLE, FETCH, SMELT, PREREQ, CRAFT, DELIVER, BUILD, REPAIR_GOLEM, REPAIR_SLOT, FETCH_REPAIR
	}

	/** One finished unit, for the GameTests. */
	public record Unit(UUID smith, long tick, String kind, String id) {}

	public static final class State {
		public Mode mode = Mode.IDLE;
		public boolean needsPlan = true;
		public long lastPlan = Long.MIN_VALUE;
		public long retryAt;
		public boolean returning;
		public boolean valid = true;
		public long lastValid;
		public final WalkTo walk = new WalkTo();
		public final Set<String> keep = new HashSet<>();
		public final Set<String> overnight = new HashSet<>();
		public SmithDecision.Hint hint = SmithDecision.Hint.NO_WORK;
		public String material = "";
		/** Set while a delivery is waiting out a full chest. Empty otherwise. */
		public String blockedHint = "";
		public @Nullable OrderKey carriedTool;
		/** Where a finished piece is walked to. Guard gear goes to Storage; a tool goes to its camp. */
		public @Nullable HeartLocation deliverTo;
		public final Map<OrderKey, Long> blockedUntil = new HashMap<>();
		public @Nullable GearOrder order;
		public @Nullable String fetchToolId;
		public @Nullable OrderKey fetchKey;
		public final Map<String, Integer> wanted = new LinkedHashMap<>();
		public @Nullable BlockPos fetchAnchor;
		public long fetchStart;
		public double progressAcc;
		public int cancelCount;
		public long lastCancelCheck = Long.MIN_VALUE;
		public int lastSavedProgress = -1;
		public int prereqIndex;
		public double prereqAcc;
		public @Nullable SmithBatch.Step batch;
		public int batchLeft;
		public double golemAcc;
		public @Nullable BlockPos waitSpot;
		public long buildStart = -1;
		public int repairIngotsLeft;
		public @Nullable UUID repairGolem;
		public long lastHeal;
		public @Nullable RepairScan.Hit repairHit;
		public List<RepairScan.Hit> repairQueue = List.of();
		public int repairQueueIndex;
		public double repairAcc;
		public boolean repairReturn;
		public @Nullable BlockPos litAt;
		public @Nullable Mode paused;
		public boolean buildFetch;
		public long lastWorkStamp;
	}

	final MinecraftServer server;
	final int epoch;
	final Map<UUID, State> workers = new HashMap<>();
	final List<Unit> units = new ArrayList<>();
	/** Smithy marker to the smith who owns today's build. */
	final Map<HeartLocation, UUID> buildOwner = new HashMap<>();

	private SmithRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	public static SmithRuntime get(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		SmithRuntime rt = current;
		if (rt == null || rt.server != server || rt.epoch != epoch) current = rt = new SmithRuntime(server, epoch);
		return rt;
	}

	public static void resetForTests() { current = null; }

	public State state(UUID id) { return workers.computeIfAbsent(id, k -> new State()); }

	public void log(Unit unit) {
		if (units.size() > 4000) units.subList(0, 2000).clear();
		units.add(unit);
	}

	public static List<Unit> unitsOf(MinecraftServer server, UUID smith) {
		List<Unit> out = new ArrayList<>();
		SmithRuntime rt = current;
		if (rt == null) return out;
		for (Unit u : rt.units) if (u.smith().equals(smith)) out.add(u);
		return out;
	}

	/** True when a smith worked this furnace within the last 40 ticks (the sweep leaves its light on). */
	public boolean working(BlockPos furnace, long now) {
		for (State st : workers.values()) {
			if (st.litAt != null && st.litAt.equals(furnace) && now - st.lastWorkStamp <= 40) return true;
		}
		return false;
	}

	/** The tab line while this smith is smithing, building, or waiting on villagers or a material. Null leaves the usual status. */
	public static @Nullable ActivityKind activity(MinecraftServer server, UUID id) {
		State st = get(server).workers.get(id);
		if (st == null) return null;
		if (st.mode == Mode.BUILD) return ActivityKind.BUILDING_GOLEM;
		if (st.mode == Mode.CRAFT || st.mode == Mode.PREREQ) return ActivityKind.SMITHING;
		if (st.hint == SmithDecision.Hint.NEEDS_VILLAGERS) return ActivityKind.NEEDS_VILLAGERS;
		if (st.hint == SmithDecision.Hint.NEEDS_MATERIAL) return ActivityKind.NEEDS_MATERIAL;
		return null;
	}

	public static SmithDecision.Hint hint(MinecraftServer server, UUID id) {
		State st = get(server).workers.get(id);
		return st == null ? SmithDecision.Hint.NO_WORK : st.hint;
	}

	public static String material(MinecraftServer server, UUID id) {
		State st = get(server).workers.get(id);
		return st == null ? "" : st.material;
	}

	public static String blockedHint(MinecraftServer server, UUID id) {
		State st = get(server).workers.get(id);
		return st == null || st.blockedHint == null ? "" : st.blockedHint;
	}

	public static boolean carrying(MinecraftServer server, OrderKey key) { return carrying(key); }

	public static boolean carrying(OrderKey key) {
		SmithRuntime rt = current;
		if (rt == null) return false;
		for (State st : rt.workers.values()) if (key.equals(st.carriedTool)) return true;
		return false;
	}

	/** The hired smith whose job block is this furnace, or a stable placeholder when nobody is loaded. */
	public static UUID holderFor(MinecraftServer server, HeartLocation furnace) {
		Optional<UUID> found = VillageClaims.current(server).flatMap(v -> {
			for (var w : v.workers()) {
				if (!w.jobId().equals(JobType.BLACKSMITH.id())) continue;
				var loaded = Workers.findLoaded(server, w.villager());
				if (loaded.isEmpty()) continue;
				var data = loaded.get().getAttached(WorkerAttachments.WORKER);
				if (data != null && furnace.equals(data.jobBlock())) return Optional.of(w.villager());
			}
			return Optional.empty();
		});
		return found.orElseGet(() -> UUID.nameUUIDFromBytes(("smith-furnace:" + furnace).getBytes(StandardCharsets.UTF_8)));
	}
}
