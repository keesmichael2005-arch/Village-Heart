package dev.villageheart.heart;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.heart.VillageFacts;
import dev.villageheart.heart.net.HeartNetwork;
import dev.villageheart.heart.net.HeartSnapshotPayload;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Heart screen's menu: no slots (nothing is moved by slot clicks; every write is one of our own payloads). The server side
 * sends this viewer's snapshot at most once a second and only when it changed; the client side holds the latest one.
 */
public class HeartMenu extends AbstractContainerMenu {
	// server side (null on the client)
	@Nullable private final ServerLevel level;
	@Nullable private final ServerPlayer viewer;
	private final BlockPos heart;
	private final int epoch;
	private boolean dirty;
	private long lastRefresh;
	@Nullable private HeartSnapshot lastSent;

	// both sides
	private HeartSnapshot snapshot;
	private int version;

	/** Client side: built from the opening data. */
	public HeartMenu(int id, Inventory inventory, HeartOpenData data) {
		super(ModMenus.HEART, id);
		this.level = null;
		this.viewer = null;
		this.heart = data.heart();
		this.epoch = 0;
		this.snapshot = data.snapshot();
	}

	/** Server side. {@code opening} is what the opening packet carries, so the first tick sends no duplicate. */
	public HeartMenu(int id, Inventory inventory, ServerLevel level, ServerPlayer viewer, BlockPos heart, int epoch,
			HeartSnapshot opening) {
		super(ModMenus.HEART, id);
		this.level = level;
		this.viewer = viewer;
		this.heart = heart;
		this.epoch = epoch;
		this.snapshot = opening;
		this.lastSent = opening;
		this.lastRefresh = level.getGameTime();
	}

	public HeartSnapshot snapshot() { return snapshot; }

	/** Counts snapshot changes (client): screens rebuild their lists when it moves. */
	public int version() { return version; }

	public BlockPos heartPos() { return heart; }

	/** The claim epoch this menu was opened in (server side). */
	public int epoch() { return epoch; }

	/** Client: a snapshot arrived. */
	public void accept(HeartSnapshot s) {
		if (s.equals(snapshot)) return;
		snapshot = s;
		version++;
	}

	/** Server: the last snapshot this menu actually sent (or opened with); tests compare the client's copy with it. */
	@Nullable
	public HeartSnapshot lastSent() { return lastSent; }

	/** Server: send a fresh snapshot on the next tick, whatever the refresh interval says. */
	public void markDirty() { dirty = true; }

	/** Server: every open Heart menu refreshes on its next tick (after an action changed the village). */
	public static void markAllDirty(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu instanceof HeartMenu m) m.markDirty();
		}
	}

	/** This viewer's snapshot from fresh-enough facts (the per-tick cache), or empty when there is no village. */
	static Optional<HeartSnapshot> build(MinecraftServer server, ServerPlayer player) {
		Optional<VillageFacts> facts = HeartFacts.cached(server);
		if (facts.isEmpty()) return Optional.empty();
		Rank rank = VillageClaims.current(server).map(v -> v.rankOf(player.getUUID())).orElse(Rank.VISITOR);
		return Optional.of(HeartSnapshotBuilder.build(facts.get(), rank));
	}

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (level == null || viewer == null) return;
		long now = level.getGameTime();
		if (!dirty && now - lastRefresh < Balance.HEART_REFRESH_TICKS) return;
		try (var t = VillageTicker.section()) {
			dirty = false;
			lastRefresh = now;
			Optional<HeartSnapshot> next = build(level.getServer(), viewer);
			if (next.isEmpty() || next.get().equals(lastSent)) return;
			lastSent = next.get();
			snapshot = next.get();
			HeartNetwork.send(viewer, new HeartSnapshotPayload(containerId, next.get()));
		}
	}

	/** Server: the village this menu was opened in is still the current one (no release or re-claim since). */
	public boolean epochCurrent() {
		return level != null && epoch == VillageClaims.data(level.getServer()).epoch();
	}

	/** Server: the Heart block is still there and the player is within reach of it. */
	public boolean nearHeart(Player player) {
		return level != null && stillValid(ContainerLevelAccess.create(level, heart), player, ModBlocks.VILLAGE_HEART);
	}

	@Override
	public boolean stillValid(Player player) {
		if (level == null) return true;
		return epochCurrent() && nearHeart(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

	/** The village this menu belongs to, if it still exists. */
	@Nullable
	public VillageState village() {
		return level == null ? null : VillageClaims.current(level.getServer()).orElse(null);
	}
}
