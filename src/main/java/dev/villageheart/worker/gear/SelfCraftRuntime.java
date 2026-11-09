package dev.villageheart.worker.gear;

import dev.villageheart.core.gear.BlockReason;
import dev.villageheart.core.gear.CancelReason;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.PendingFetch;
import dev.villageheart.core.gear.SelfCraftDecision;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.job.WalkTo;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** Transient per-worker state of the self-craft loop (M19a plan 3.4). Cleared when the claim changes; nothing here is saved. */
public final class SelfCraftRuntime {
	/** What one worker is doing about its own tool. */
	public static final class State {
		public final WalkTo walk = new WalkTo();
		@Nullable public PendingFetch fetching;
		/** The Storage the fetch walks to. */
		@Nullable public BlockPos storage;
		/** A usable tool is fetched from Storage instead of crafted (no record, no committed items). */
		public boolean storedFetch;
		@Nullable public net.minecraft.world.item.ItemStack storedWanted;
		public long fetchTick;
		public long retryAt;
		public long lastPlan = Long.MIN_VALUE;
		@Nullable public SelfCraftDecision decision;
		/** True while this worker owns the tick (the job's own tick is skipped). */
		public boolean owning;
		/** Set while the craft takes over a tick and calls the job's own stop, so that stop does not undo the craft. */
		public boolean suppressStop;
		/** Work ticks the slot has been open without an order (the smith fallback). */
		public long slotOpenTicks;
		/** The craft's progress in work ticks (the saved record is only written every SELF_CRAFT_SAVE_TICKS). */
		public double progress = -1;
		@Nullable public String progressRecipe;
		@Nullable public OrderKey progressKey;
		public long lastSaveTick;
		public long lastHitProgress;
		@Nullable public BlockReason block;
		/** The cancel hysteresis (SelfCraftSystem). */
		public int cancelCount;
		@Nullable public CancelReason cancelReason;
		/** Game time the building was first seen invalid (-1: valid). */
		public long invalidSince = -1;
	}

	private final MinecraftServer server;
	private final int epoch;
	final Map<UUID, State> workers = new HashMap<>();

	private static SelfCraftRuntime current;

	private SelfCraftRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	public static SelfCraftRuntime get(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		SelfCraftRuntime rt = current;
		if (rt == null || rt.server != server || rt.epoch != epoch) {
			current = rt = new SelfCraftRuntime(server, epoch);
			GearOrders.reset();
			VillageClaims.current(server).ifPresent(GearOrders::rebuild);
		}
		return rt;
	}

	public State state(UUID worker) { return workers.computeIfAbsent(worker, k -> new State()); }

	@Nullable
	public State peek(UUID worker) { return workers.get(worker); }

	/** The reason this worker's required tool cannot be made now, for the Villagers tab (preferred over "Needs axe"). */
	@Nullable
	public static BlockReason blockOf(MinecraftServer server, UUID worker) {
		SelfCraftRuntime rt = current;
		if (rt == null || rt.server != server) return null;
		State s = rt.workers.get(worker);
		return s == null ? null : s.block;
	}

	/** Tests: forget everything, as after a restart. */
	public static void resetForTests() {
		current = null;
		GearOrders.reset();
	}
}
