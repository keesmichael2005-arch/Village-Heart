package dev.villageheart.worker.pocket;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.job.ChestOwnership;
import dev.villageheart.core.job.ChestOwnership.Chest;
import dev.villageheart.core.job.ChestOwnership.Spot;
import dev.villageheart.core.job.PocketRules;
import dev.villageheart.core.job.ToolDay;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomContents;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.CampContainers;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

/**
 * A hired villager's own chest and the eight slots they carry. The tool of a lumberjack, miner, hunter or farmer sits in slot 0 during the
 * day and goes back into that chest at the end of work. Nobody else takes from the chest.
 */
public final class Pockets {
	private Pockets() {}

	public static Spot spot(HeartLocation l) { return new Spot(l.x(), l.y(), l.z()); }

	public static HeartLocation locate(String dimension, Spot s) { return new HeartLocation(dimension, s.x(), s.y(), s.z()); }

	public static BlockPos pos(HeartLocation l) { return new BlockPos(l.x(), l.y(), l.z()); }

	/** Chests in a scanned room. A double chest is one entry (both halves). */
	public static List<Chest> chests(ServerLevel level, RoomScan scan) {
		List<Chest> out = new ArrayList<>();
		for (Pos p : scan.positions(RoomContents.CHEST)) {
			BlockPos at = new BlockPos(p.x(), p.y(), p.z());
			BlockPos other = partner(level, at);
			out.add(new Chest(new Spot(at.getX(), at.getY(), at.getZ()),
					other == null ? null : new Spot(other.getX(), other.getY(), other.getZ())));
		}
		return out;
	}

	/** The other half of a double chest, or null. */
	public static @Nullable BlockPos partner(ServerLevel level, BlockPos at) {
		BlockState state = level.getBlockState(at);
		if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
		return at.relative(ChestBlock.getConnectedDirection(state));
	}

	/**
	 * The nearest chest in {@code building} that no worker owns, for a new hire at {@code jobBlock}. Empty when every chest is taken
	 * or the room cannot be read.
	 */
	public static Optional<HeartLocation> nearestFree(MinecraftServer server, HeartLocation building, HeartLocation jobBlock,
			List<WorkerRecord> workers) {
		Optional<Buildings.BuildingView> view = CampContainers.building(server, building);
		if (view.isEmpty() || view.get().latestScan().isEmpty()) return Optional.empty();
		ServerLevel level = view.get().level();
		List<Chest> chests = chests(level, view.get().latestScan().get());
		Set<Spot> taken = new HashSet<>();
		for (WorkerRecord w : workers) w.chest().ifPresent(c -> taken.add(spot(c)));
		return ChestOwnership.nearestFree(spot(jobBlock), chests, taken)
				.map(c -> locate(building.dimension(), c.key()));
	}

	/** Old saves: pair chests that were never stored. A broken link is left alone. Runs on the village tick. */
	public static void migrate(MinecraftServer server, VillageState village) {
		var byBuilding = new java.util.LinkedHashMap<HeartLocation, List<WorkerRecord>>();
		for (WorkerRecord w : village.workers()) byBuilding.computeIfAbsent(w.building(), k -> new ArrayList<>()).add(w);
		for (var e : byBuilding.entrySet()) {
			Optional<Buildings.BuildingView> view = CampContainers.building(server, e.getKey());
			if (view.isEmpty() || view.get().latestScan().isEmpty()) continue;
			ServerLevel level = view.get().level();
			List<ChestOwnership.Held> held = new ArrayList<>();
			for (WorkerRecord w : e.getValue()) held.add(new ChestOwnership.Held(w.villager(), spot(w.jobBlock()), w.chest().map(Pockets::spot).orElse(null)));
			var assigned = ChestOwnership.resolve(held, chests(level, view.get().latestScan().get()));
			for (WorkerRecord w : e.getValue()) {
				Spot now = assigned.get(w.villager());
				HeartLocation next = now == null ? null : locate(e.getKey().dimension(), now);
				if (w.chest().equals(Optional.ofNullable(next))) continue;
				// A broken chest stays. resolve keeps a link that is not in the room; a null result means "no chest".
				if (w.chest().isPresent() && next == null && !inRoom(view.get().latestScan().get(), w.chest().get())) continue;
				VillageClaims.data(server).update(s -> s.withWorkerChest(w.villager(), next));
				Villager v = Workers.findLoaded(server, w.villager()).orElse(null);
				WorkerData data = v == null ? null : v.getAttached(WorkerAttachments.WORKER);
				if (data != null) v.setAttached(WorkerAttachments.WORKER, data.withChest(next));
			}
		}
	}

	private static boolean inRoom(RoomScan scan, HeartLocation chest) {
		for (Pos p : scan.positions(RoomContents.CHEST)) {
			if (p.x() == chest.x() && p.y() == chest.y() && p.z() == chest.z()) return true;
		}
		return false;
	}

	/** True when the saved chest is in a loaded chunk and is no longer a chest. An unloaded chunk is not "missing". */
	public static boolean chestMissing(MinecraftServer server, WorkerRecord record) {
		if (record.chest().isEmpty()) return false;
		HeartLocation c = record.chest().get();
		Optional<Buildings.BuildingView> view = CampContainers.building(server, record.building());
		if (view.isEmpty()) return false;
		ServerLevel level = view.get().level();
		BlockPos at = pos(c);
		if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) return false;
		return !(level.getBlockState(at).getBlock() instanceof ChestBlock);
	}

	/** A registered worker whose room can be read and who still has no chest. */
	public static boolean waitingOnChest(MinecraftServer server, WorkerRecord record) {
		if (record.chest().isPresent()) return false;
		return CampContainers.building(server, record.building()).filter(b -> b.latestScan().isPresent()).isPresent();
	}

	public static boolean reserves(Villager v) {
		return WorkerAttachments.get(v).map(d -> PocketRules.reservesToolSlot(d.job())).orElse(false);
	}

	public static @Nullable ToolKind toolKind(Villager v) {
		return WorkerAttachments.get(v).flatMap(d -> d.job().tool()).map(r -> r.kind()).orElse(null);
	}

	/** Slot 0 holds this worker's tool. */
	public static boolean holdingTool(Villager v) {
		ToolKind kind = toolKind(v);
		if (kind == null || !reserves(v)) return false;
		return CampTools.isTool(kind, v.getInventory().getItem(PocketRules.TOOL_SLOT));
	}

	/** Goods that landed in the tool slot move to a cargo slot, or drop, so the replacement still has a place. */
	public static void settleToolSlot(Villager v) {
		if (!reserves(v)) return;
		var inv = v.getInventory();
		ItemStack s = inv.getItem(PocketRules.TOOL_SLOT);
		if (s.isEmpty()) return;
		ToolKind kind = toolKind(v);
		if (kind != null && CampTools.isTool(kind, s)) return;
		boolean[] occupied = new boolean[PocketRules.SLOTS];
		for (int i = 0; i < inv.getContainerSize() && i < PocketRules.SLOTS; i++) occupied[i] = !inv.getItem(i).isEmpty();
		occupied[PocketRules.TOOL_SLOT] = true;
		int dest = PocketRules.placeGoods(v.getAttached(WorkerAttachments.WORKER).job(), occupied);
		if (dest < 0) {
			if (v.level() instanceof ServerLevel level) v.spawnAtLocation(level, s.copy());
			inv.setItem(PocketRules.TOOL_SLOT, ItemStack.EMPTY);
			return;
		}
		inv.setItem(dest, s.copy());
		inv.setItem(PocketRules.TOOL_SLOT, ItemStack.EMPTY);
	}

	/** Puts goods in a cargo slot. What does not fit is returned. The tool slot is never used. */
	public static ItemStack insertGoods(Villager v, ItemStack stack) {
		if (stack.isEmpty()) return stack;
		settleToolSlot(v);
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || !PocketRules.reservesToolSlot(data.job())) return v.getInventory().addItem(stack);
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
			if (!PocketRules.acceptsGoods(data.job(), i)) continue;
			ItemStack have = inv.getItem(i);
			if (have.isEmpty()) continue;
			if (!ItemStack.isSameItemSameComponents(have, stack)) continue;
			int n = Math.min(stack.getCount(), have.getMaxStackSize() - have.getCount());
			if (n <= 0) continue;
			have.grow(n);
			stack.shrink(n);
		}
		for (int i = 0; i < inv.getContainerSize() && !stack.isEmpty(); i++) {
			if (!PocketRules.acceptsGoods(data.job(), i) || !inv.getItem(i).isEmpty()) continue;
			inv.setItem(i, stack.copy());
			stack.setCount(0);
		}
		return stack;
	}

	public static boolean cargoFull(Villager v) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null) return false;
		var inv = v.getInventory();
		boolean[] occupied = new boolean[PocketRules.SLOTS];
		for (int i = 0; i < PocketRules.SLOTS && i < inv.getContainerSize(); i++) occupied[i] = !inv.getItem(i).isEmpty();
		return PocketRules.cargoFull(data.job(), occupied);
	}

	/** Evening, the tool is still in the pocket, and the chest can take it: the dump waits. */
	public static boolean mustReturnTool(ServerLevel level, Villager v) {
		if (DaySchedule.phaseAt(level.getDayTime()) != DayPhase.GO_HOME) return false;
		if (!holdingTool(v)) return false;
		return chestHasFreeSlot(level.getServer(), v);
	}

	public static boolean chestHasFreeSlot(MinecraftServer server, Villager v) {
		for (Container c : ownContainers(server, v)) {
			for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).isEmpty()) return true;
		}
		return false;
	}

	public static boolean chestHasTool(MinecraftServer server, Villager v) {
		ToolKind kind = toolKind(v);
		if (kind == null) return false;
		for (Container c : ownContainers(server, v)) {
			for (int i = 0; i < c.getContainerSize(); i++) if (CampTools.isTool(kind, c.getItem(i))) return true;
		}
		return false;
	}

	/** One half of the worker's chest. */
	public record Owned(BlockPos pos, Container container) {}

	/** The worker's chest, both halves of a double chest. Empty when they have none or it cannot be read. */
	public static List<Owned> ownChests(MinecraftServer server, Villager v) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null || data.chest().isEmpty()) return List.of();
		Optional<Buildings.BuildingView> view = CampContainers.building(server, data.building());
		if (view.isEmpty()) return List.of();
		ServerLevel level = view.get().level();
		BlockPos at = pos(data.chest().get());
		if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) return List.of();
		List<Owned> out = new ArrayList<>();
		add(level, at, out);
		BlockPos other = partner(level, at);
		if (other != null) add(level, other, out);
		return out;
	}

	public static List<Container> ownContainers(MinecraftServer server, Villager v) {
		List<Container> out = new ArrayList<>();
		for (Owned o : ownChests(server, v)) out.add(o.container());
		return out;
	}

	private static void add(ServerLevel level, BlockPos at, List<Owned> out) {
		BlockEntity be = level.getBlockEntity(at);
		if (be instanceof Container c && !be.isRemoved()) out.add(new Owned(at, c));
	}

	/** Moves the best tool from the worker's chest into slot 0. False when there is nothing to take or the slot is taken. */
	public static boolean takeTool(MinecraftServer server, Villager v) {
		if (!reserves(v) || holdingTool(v)) return false;
		settleToolSlot(v);
		if (!v.getInventory().getItem(PocketRules.TOOL_SLOT).isEmpty()) return false;
		ToolKind kind = toolKind(v);
		if (kind == null) return false;
		Container bestC = null;
		int bestSlot = -1;
		ToolTier bestTier = null;
		int bestLeft = Integer.MAX_VALUE;
		for (Container c : ownContainers(server, v)) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!CampTools.isTool(kind, s)) continue;
				ToolTier tier = CampTools.tierOf(s);
				int left = Math.max(0, s.getMaxDamage() - s.getDamageValue());
				if (bestTier == null || tier.ordinal() > bestTier.ordinal() || (tier == bestTier && left < bestLeft)) {
					bestTier = tier;
					bestLeft = left;
					bestC = c;
					bestSlot = i;
				}
			}
		}
		if (bestC == null) return false;
		ItemStack tool = bestC.getItem(bestSlot);
		v.getInventory().setItem(PocketRules.TOOL_SLOT, tool.copy());
		bestC.setItem(bestSlot, ItemStack.EMPTY);
		bestC.setChanged();
		return true;
	}

	/** Puts slot 0's tool into the worker's chest. False when the chest has no free slot: the tool stays overnight. */
	public static boolean returnTool(MinecraftServer server, Villager v) {
		if (!holdingTool(v)) return false;
		ItemStack tool = v.getInventory().getItem(PocketRules.TOOL_SLOT);
		for (Container c : ownContainers(server, v)) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (!c.getItem(i).isEmpty()) continue;
				c.setItem(i, tool.copy());
				c.setChanged();
				v.getInventory().setItem(PocketRules.TOOL_SLOT, ItemStack.EMPTY);
				return true;
			}
		}
		return false;
	}

	/**
	 * Death or a zombie: the tool goes home when they were holding one. Whatever is still in the pocket is dropped. A villager's own
	 * inventory is not death loot, so the cargo would otherwise vanish with them.
	 */
	public static void onDeath(ServerLevel level, Villager v) {
		ToolDay.Death death = ToolDay.onDeath(holdingTool(v));
		if (death.toolToChest()) returnTool(level.getServer(), v);
		if (!death.dropCargo()) return;
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			inv.setItem(i, ItemStack.EMPTY);
			net.minecraft.world.Containers.dropItemStack(level, v.getX(), v.getY(), v.getZ(), stack);
		}
	}

	/**
	 * Where a new tool is delivered: the chest of the first worker at that building whose job uses that tool and who does not already
	 * have one. Null when nobody there is waiting, so the caller can fall back to the building's first chest.
	 */
	public static @Nullable BlockPos deliveryChest(MinecraftServer server, HeartLocation building, String itemId) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return null;
		ItemStack probe = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(itemId)));
		for (WorkerRecord w : village.get().workers()) {
			if (!w.building().equals(building) || w.chest().isEmpty()) continue;
			JobType job = JobType.byId(w.jobId()).orElse(null);
			if (job == null || job.tool().isEmpty() || !CampTools.isTool(job.tool().get().kind(), probe)) continue;
			Villager v = Workers.findLoaded(server, w.villager()).orElse(null);
			if (v == null) continue;
			if (holdingTool(v) || chestHasTool(server, v)) continue;
			if (chestHasFreeSlot(server, v)) return pos(w.chest().get());
		}
		return null;
	}
}
