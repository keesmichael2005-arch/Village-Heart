package dev.villageheart.building;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.ClaimArea;
import dev.villageheart.core.building.Reason;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The building registry (DESIGN section 3.2): registers markers when they are placed, re-checks one building per second
 * in a round-robin and immediately on use, and unregisters them when they go. Server thread only.
 */
public final class BuildingSystem implements VillageSystem {
	public static final BuildingSystem INSTANCE = new BuildingSystem();

	@Nullable
	private static BuildingRuntime runtime;

	private BuildingSystem() {}

	static BuildingRuntime runtime(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		BuildingRuntime rt = runtime;
		if (rt == null || rt.server != server || rt.epoch != epoch) runtime = rt = new BuildingRuntime(server, epoch);
		return rt;
	}

	/** Tests: forget every cache, as after a restart. */
	public static void resetRuntimeForTests() {
		runtime = null;
		dev.villageheart.worker.hunt.HunterRuntime.resetForTests();
	}

	/** Tests: markers noted by onPlace since the runtime was created. */
	public static int placedNotesForTests(MinecraftServer server) { return runtime(server).placedNotes; }

	/** Tests: markers waiting for registration, and queued priority checks. */
	public static int pendingForTests(MinecraftServer server) { return runtime(server).pending.size(); }

	public static int priorityQueuedForTests(MinecraftServer server) { return runtime(server).schedule.pendingPriority(); }

	// ---- hooks from MarkerBlock ------------------------------------------------------------------

	/** A new marker block appeared (onPlace, never for a recolour). No world writes here: it is registered next tick. */
	public static void notePlaced(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (VillageClaims.current(server).isEmpty()) return;
		BuildingRuntime rt = runtime(server);
		rt.pending.add(VillageClaims.locate(level, pos));
		rt.placedNotes++;
	}

	/** A marker block was removed or replaced (affectNeighborsAfterRemoval): unregister it. */
	public static void noteRemoved(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return;
		HeartLocation loc = VillageClaims.locate(level, pos);
		Optional<BuildingRecord> record = village.get().building(loc);
		if (record.isEmpty()) return;
		if (level.getBlockState(pos).getBlock() instanceof MarkerBlock now && now.type().id().equals(record.get().typeId())) return;
		unregister(runtime(server), loc, record.get());
	}

	// ---- per tick --------------------------------------------------------------------------------

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		BuildingRuntime rt = runtime(server);
		if (!rt.pending.isEmpty()) {
			for (HeartLocation loc : List.copyOf(rt.pending)) register(server, rt, loc);
			rt.pending.clear();
		}
		VillageState now = VillageClaims.current(server).orElse(null);
		if (now == null) return;
		if (!rt.primed) {
			rt.primed = true;
			for (BuildingRecord b : now.buildings()) rt.schedule.prioritise(b.marker());
		}
		for (HeartLocation loc : rt.schedule.tick(rt.sortedMarkers(now))) BuildingValidator.validate(server, rt, loc);
	}

	// ---- registration ----------------------------------------------------------------------------

	/**
	 * Register the marker at {@code loc} (or mark it INVALID when it is outside the claim). Returns the record when it is
	 * registered now. Never loads a chunk.
	 */
	@Nullable
	static BuildingRecord register(MinecraftServer server, BuildingRuntime rt, HeartLocation loc) {
		ServerLevel level = levelOf(server, loc);
		Optional<VillageState> village = VillageClaims.current(server);
		if (level == null || village.isEmpty()) return null;
		BlockPos pos = new BlockPos(loc.x(), loc.y(), loc.z());
		if (!isLoaded(level, pos)) return null;
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof MarkerBlock marker)) return null;
		BuildingType type = marker.type();
		Optional<BuildingRecord> existing = village.get().building(loc);
		if (existing.isPresent()) {
			if (existing.get().typeId().equals(type.id())) return existing.get();
			unregister(rt, loc, existing.get()); // replaced by a different marker type
		}
		Reason outside = ClaimArea.check(village.get().heart(), loc, type);
		if (outside != null) {
			setStatus(level, pos, state, BuildingStatus.INVALID);
			return null;
		}
		BuildingRecord record = new BuildingRecord(type.id(), loc);
		VillageClaims.data(server).update(s -> s.withBuilding(record));
		rt.schedule.prioritise(loc);
		prioritiseDependents(rt, VillageClaims.current(server).orElseThrow(), type);
		return record;
	}

	static void unregister(BuildingRuntime rt, HeartLocation loc, BuildingRecord record) {
		MinecraftServer server = rt.server;
		Optional<VillageState> before = VillageClaims.current(server);
		if (before.isEmpty()) return;
		// the room partners must hear about it, and its scan must leave the shared-room set first
		RoomScan gone = rt.rawScans.get(loc);
		rt.forget(loc);
		VillageClaims.data(server).update(s -> s.withoutBuilding(loc));
		VillageState village = VillageClaims.current(server).orElseThrow();
		if (gone != null && gone.enclosed()) {
			for (var e : rt.rawScans.entrySet()) {
				if (e.getValue().enclosed() && e.getValue().roomKey() == gone.roomKey()) rt.schedule.prioritise(e.getKey());
			}
		}
		record.type().ifPresent(t -> prioritiseDependents(rt, village, t));
		if (record.link() != null) rt.schedule.prioritise(record.link());
	}

	/** A parent type was registered or removed: its sites may have gained or lost their link. */
	static void prioritiseDependents(BuildingRuntime rt, VillageState village, BuildingType changed) {
		List<BuildingType> children = new ArrayList<>();
		switch (changed) {
			case MINE -> children.add(BuildingType.MINESHAFT);
			case HUNTING_LODGE -> children.add(BuildingType.HUNTING_GROUND);
			case BARRACKS -> children.add(BuildingType.TRAINING_YARD);
			case HOUSE, FARMHOUSE -> children.add(BuildingType.HUNTING_GROUND); // the home-room exclusion
			default -> {}
		}
		if (children.isEmpty()) return;
		for (BuildingRecord b : village.buildings()) {
			for (BuildingType c : children) if (b.typeId().equals(c.id())) rt.schedule.prioritise(b.marker());
		}
	}

	// ---- right-click -----------------------------------------------------------------------------

	/** The right-click: check now and tell the player every reason. */
	public static void useMarker(ServerLevel level, BlockPos pos, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		try (var t = VillageTicker.section()) {
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof MarkerBlock marker)) return;
			BuildingType type = marker.type();
			Optional<VillageState> village = VillageClaims.current(server);
			if (village.isEmpty()) {
				setStatus(level, pos, state, BuildingStatus.UNCHECKED);
				player.sendSystemMessage(MarkerReport.noVillage());
				return;
			}
			HeartLocation loc = VillageClaims.locate(level, pos);
			BuildingRuntime rt = runtime(server);
			BuildingRecord record = register(server, rt, loc);
			Verdict verdict;
			if (record != null) {
				verdict = BuildingValidator.validate(server, rt, loc);
				if (verdict == null) verdict = rt.verdicts.get(loc);
			} else {
				Reason outside = ClaimArea.check(village.get().heart(), loc, type);
				verdict = outside != null ? Verdict.of(List.of(outside), 0, null) : null;
			}
			if (verdict == null) return;
			for (Component line : MarkerReport.lines(type, new dev.villageheart.core.room.Pos(pos.getX(), pos.getY(), pos.getZ()), verdict)) {
				player.sendSystemMessage(line);
			}
		}
	}

	// ---- helpers ---------------------------------------------------------------------------------

	@Nullable
	static ServerLevel levelOf(MinecraftServer server, HeartLocation loc) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(loc.dimension())));
	}

	static boolean isLoaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** Recolour the plaque (UPDATE_CLIENTS only: MarkerBlock.onPlace ignores a state change of the same block). */
	static void setStatus(ServerLevel level, BlockPos pos, BlockState state, BuildingStatus status) {
		MarkerStatus wanted = MarkerStatus.valueOf(status.name());
		if (state.getValue(MarkerBlock.STATUS) != wanted) level.setBlock(pos, state.setValue(MarkerBlock.STATUS, wanted), Block.UPDATE_CLIENTS);
	}
}
