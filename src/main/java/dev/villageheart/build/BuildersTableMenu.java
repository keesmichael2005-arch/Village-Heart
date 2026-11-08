package dev.villageheart.build;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.build.net.BuildersTableSnapshotPayload;
import dev.villageheart.core.Balance;
import dev.villageheart.core.blueprint.TableSnapshot;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.heart.ModMenus;
import dev.villageheart.heart.net.HeartNetwork;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Builder's Table menu: no slots (like the Heart's), a snapshot sent to the viewer at most once a second and only when it changed. Every
 * write is one of our payloads, re-checked on the server ({@link BuildersTableActions}).
 */
public class BuildersTableMenu extends AbstractContainerMenu {
	@Nullable private final ServerLevel level;
	@Nullable private final ServerPlayer viewer;
	private final BlockPos table;
	private final int epoch;
	private boolean dirty;
	private long lastRefresh;
	@Nullable private TableSnapshot lastSent;
	private TableSnapshot snapshot;
	private int version;

	/** Client side. */
	public BuildersTableMenu(int id, Inventory inventory, BuildersTableOpenData data) {
		super(ModMenus.BUILDERS_TABLE, id);
		this.level = null;
		this.viewer = null;
		this.table = data.table();
		this.epoch = 0;
		this.snapshot = data.snapshot();
	}

	/** Server side. */
	public BuildersTableMenu(int id, Inventory inventory, ServerLevel level, ServerPlayer viewer, BlockPos table, int epoch, TableSnapshot opening) {
		super(ModMenus.BUILDERS_TABLE, id);
		this.level = level;
		this.viewer = viewer;
		this.table = table;
		this.epoch = epoch;
		this.snapshot = opening;
		this.lastSent = opening;
		this.lastRefresh = level.getGameTime();
	}

	public TableSnapshot snapshot() { return snapshot; }

	/** Counts snapshot changes (client). */
	public int version() { return version; }

	public BlockPos tablePos() { return table; }

	public int epoch() { return epoch; }

	/** Client: a snapshot arrived. */
	public void accept(TableSnapshot s) {
		if (s.equals(snapshot)) return;
		snapshot = s;
		version++;
	}

	@Nullable
	public TableSnapshot lastSent() { return lastSent; }

	public void markDirty() { dirty = true; }

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (level == null || viewer == null) return;
		long now = level.getGameTime();
		if (!dirty && now - lastRefresh < Balance.HEART_REFRESH_TICKS) return;
		try (var t = VillageTicker.section()) {
			dirty = false;
			lastRefresh = now;
			Optional<TableSnapshot> next = BuildersTableFacts.snapshot(level.getServer(), viewer);
			if (next.isEmpty() || next.get().equals(lastSent)) return;
			lastSent = next.get();
			snapshot = next.get();
			HeartNetwork.send(viewer, new BuildersTableSnapshotPayload(containerId, next.get()));
		}
	}

	public boolean epochCurrent() { return level != null && epoch == VillageClaims.data(level.getServer()).epoch(); }

	/** Server: the table block is still there and the player is within reach of it. */
	public boolean nearTable(Player player) {
		return level != null && stillValid(ContainerLevelAccess.create(level, table), player, JobBlocks.BLOCKS.get(JobBlockType.BUILDERS_TABLE));
	}

	@Override
	public boolean stillValid(Player player) {
		if (level == null) return true;
		return epochCurrent() && nearTable(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
