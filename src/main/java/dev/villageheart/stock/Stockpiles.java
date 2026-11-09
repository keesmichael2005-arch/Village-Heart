package dev.villageheart.stock;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.stock.Stockpile;
import dev.villageheart.core.stock.StorageAlert;
import dev.villageheart.core.stock.StorageAlertTracker;
import dev.villageheart.core.stock.StorageSite;
import dev.villageheart.room.RoomScans;
import dev.villageheart.world.VillageClaims;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What later milestones call: the village stockpile, a live view over the chests and barrels of the valid Storage buildings
 * (DESIGN section 6). <b>Server thread only</b>: every method refuses other threads. Nothing is cached between calls, so a
 * player editing a chest is seen by the very next call.
 */
public final class Stockpiles {
	private Stockpiles() {}

	/** Transient state: dropped on a server change and when the claim changes. */
	static final class Runtime {
		final MinecraftServer server;
		final int epoch;
		final Reservations<StackKey> reservations = new Reservations<>();
		final StorageAlertTracker tracker = new StorageAlertTracker();

		Runtime(MinecraftServer server, int epoch) {
			this.server = server;
			this.epoch = epoch;
		}
	}

	@Nullable
	private static Runtime runtime;

	static Runtime runtime(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		Runtime rt = runtime;
		if (rt == null || rt.server != server || rt.epoch != epoch) runtime = rt = new Runtime(server, epoch);
		return rt;
	}

	/** Tests: forget the reservations and the alert, as after a restart. */
	public static void resetRuntimeForTests() { runtime = null; }

	private static void requireServerThread(MinecraftServer server) {
		if (!server.isSameThread()) throw new IllegalStateException("The stockpile must be used on the server thread");
	}

	private static Stockpile<StackKey> view(MinecraftServer server, StorageSites.Collected c) {
		return new Stockpile<>(c.sites(), runtime(server).reservations);
	}

	// ---- reading ---------------------------------------------------------------------------------

	/** Computed fresh on every call. */
	public static StockSnapshot<StackKey> snapshot(MinecraftServer server) {
		requireServerThread(server);
		StorageSites.Collected c = StorageSites.collect(server);
		Runtime rt = runtime(server);
		long now = server.overworld().getGameTime();
		if (c.level() == null) return new StockSnapshot<>(now, List.of(), 0, 0, 0, 0, 0, 0, StorageAlert.NO_STORAGE);
		return view(server, c).snapshot(now, c.unknownSites(), c.unreadContainers(), rt.tracker.state());
	}

	/** All variants of the item. */
	public static long count(MinecraftServer server, Item item) {
		return count(server, k -> k.item() == item);
	}

	public static long count(MinecraftServer server, TagKey<Item> tag) {
		return count(server, k -> k.is(tag));
	}

	public static long count(MinecraftServer server, StackKey key) {
		requireServerThread(server);
		return view(server, StorageSites.collect(server)).count(key);
	}

	private static long count(MinecraftServer server, Predicate<StackKey> match) {
		requireServerThread(server);
		return view(server, StorageSites.collect(server)).count(match);
	}

	/** Item id to count, summed over variants; sorted. Empty when there is no claim. */
	public static Map<String, Long> totalsById(MinecraftServer server) {
		return snapshot(server).totalsBy(StackKey::id);
	}

	/** M19a: the level the Storage buildings are in, or empty when there is none (a refund needs it to deposit). */
	public static Optional<ServerLevel> storageLevel(MinecraftServer server) {
		requireServerThread(server);
		return Optional.ofNullable(StorageSites.collect(server).level());
	}

	/** M7+: the "stop gathering" check. */
	public static boolean hasSpaceFor(ServerLevel level, ItemStack stack) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StackKey key = StackKey.of(stack);
		StorageSites.Collected c = StorageSites.collect(server);
		return key != null && c.level() == level && view(server, c).hasSpaceFor(key);
	}

	/** The anchor (door) of the nearest Storage with space for the stack: where a worker walks to. */
	public static Optional<BlockPos> nearestWithSpace(ServerLevel level, BlockPos from, ItemStack stack) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StackKey key = StackKey.of(stack);
		StorageSites.Collected c = StorageSites.collect(server);
		if (key == null || c.level() != level) return Optional.empty();
		return view(server, c).nearestWithSpace(RoomScans.pos(from), key).map(s -> RoomScans.blockPos(s.anchor()));
	}

	/** M20: the anchor (door) of the nearest Storage that holds an item matching {@code match}: where a builder walks to fetch materials. */
	public static Optional<BlockPos> nearestHolding(ServerLevel level, BlockPos from, Predicate<StackKey> match) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StorageSites.Collected c = StorageSites.collect(server);
		if (c.level() != level) return Optional.empty();
		return view(server, c).nearestHolding(RoomScans.pos(from), match).map(s -> RoomScans.blockPos(s.anchor()));
	}

	// ---- writing ---------------------------------------------------------------------------------

	/** Moves as much of {@code stack} as fits (nearest first, spilling); shrinks the stack by that amount; returns it. */
	public static int deposit(ServerLevel level, BlockPos from, ItemStack stack) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StackKey key = StackKey.of(stack);
		StorageSites.Collected c = StorageSites.collect(server);
		if (key == null || c.level() != level) return 0;
		int asked = stack.getCount();
		int stored = view(server, c).deposit(RoomScans.pos(from), key, asked);
		stack.shrink(stored);
		if (stored < asked) runtime(server).tracker.noteDepositShort(level.getGameTime(), c.sites().size(), c.unknownSites());
		return stored;
	}

	/**
	 * Same, into the Storage whose anchor is nearest to {@code from} only (a worker standing at that Storage). Never raises the
	 * "Storage full" alert, even when it stores nothing: one full Storage is not a full village.
	 */
	public static int depositHere(ServerLevel level, BlockPos from, ItemStack stack) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StackKey key = StackKey.of(stack);
		StorageSites.Collected c = StorageSites.collect(server);
		if (key == null || c.level() != level || c.sites().isEmpty()) return 0;
		Pos p = RoomScans.pos(from);
		Stockpile<StackKey> sp = view(server, c);
		StorageSite<StackKey> nearest = sp.sites().stream().min(Comparator.comparingLong(s -> dist2(p, s.anchor()))).orElseThrow();
		int stored = sp.depositInto(nearest, key, stack.getCount());
		stack.shrink(stored);
		return stored;
	}

	private static long dist2(Pos a, Pos b) {
		long dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}

	/** Up to {@code max} (capped at one stack) of {@code key}; EMPTY when none. */
	public static ItemStack withdraw(ServerLevel level, @Nullable BlockPos from, StackKey key, int max, @Nullable UUID holder) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StorageSites.Collected c = StorageSites.collect(server);
		if (c.level() != level) return ItemStack.EMPTY;
		int taken = view(server, c).withdraw(from == null ? null : RoomScans.pos(from), key, Math.min(max, key.maxStackSize()), holder);
		return taken == 0 ? ItemStack.EMPTY : key.toStack(taken);
	}

	/** Up to {@code max} of the first matching variant found, nearest first. */
	public static ItemStack withdrawFirst(ServerLevel level, @Nullable BlockPos from, Predicate<StackKey> match, int max, @Nullable UUID holder) {
		MinecraftServer server = level.getServer();
		requireServerThread(server);
		StorageSites.Collected c = StorageSites.collect(server);
		if (c.level() != level) return ItemStack.EMPTY;
		return view(server, c).withdrawFirst(from == null ? null : RoomScans.pos(from), match, StackKey::maxStackSize, max, holder)
				.map(t -> t.key().toStack(t.count())).orElse(ItemStack.EMPTY);
	}

	// ---- reservations and alert ------------------------------------------------------------------

	public static Reservations<StackKey> reservations(MinecraftServer server) {
		requireServerThread(server);
		return runtime(server).reservations;
	}

	public static StorageAlert alert(MinecraftServer server) {
		requireServerThread(server);
		return runtime(server).tracker.state();
	}

	public static long alertSince(MinecraftServer server) {
		requireServerThread(server);
		return runtime(server).tracker.since();
	}

	/** The village system's periodic refresh of the alert (public so a GameTest can time one refresh). */
	public static void refreshAlert(MinecraftServer server) {
		runtime(server).tracker.refresh(snapshot(server), server.overworld().getGameTime());
	}
}
