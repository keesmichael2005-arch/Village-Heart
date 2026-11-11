package dev.villageheart.worker.shelter;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.raid.ShelterPlanner;
import dev.villageheart.core.room.Pos;
import dev.villageheart.needs.Housing;
import dev.villageheart.worker.job.WalkTo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Where a sheltering worker is walking, and the targets already found unreachable (M25 plan 3.2). Not saved: a reload chooses again.
 */
public final class ShelterRuntime {
	private static final Map<UUID, Walk> WALKS = new HashMap<>();

	private ShelterRuntime() {}

	public static void resetForTests() { WALKS.clear(); }

	/** True when this worker has a shelter to stand in. False is the Heart's "no shelter". */
	public static boolean hasTarget(Villager v) {
		Walk w = WALKS.get(v.getUUID());
		return w != null && w.goal != null;
	}

	public static void release(Villager v) {
		Walk w = WALKS.remove(v.getUUID());
		if (w != null) w.walk.release(v);
	}

	public static void tick(ServerLevel level, Villager v, long now) {
		Housing.rebuild(level.getServer());
		Walk w = WALKS.computeIfAbsent(v.getUUID(), id -> new Walk());
		Optional<ShelterPlanner.ShelterTarget> choice = pick(level, v, w.rejected);
		if (choice.isEmpty()) {
			w.goal = null;
			w.walk.release(v);
			return;
		}
		Pos at = choice.get().pos();
		BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
		BlockPos look = lookAt(level, choice.get());
		if (w.goal == null || !w.goal.equals(pos)) {
			w.goal = pos;
			w.look = look;
			w.begun = now;
			w.walk.begin(pos, Balance.SHELTER_ARRIVE, now);
		} else if (now - w.begun >= Balance.SHELTER_REPATH_TICKS && WalkTo.distance(v, pos) > Balance.SHELTER_ARRIVE) {
			w.begun = now;
			w.walk.begin(pos, Balance.SHELTER_ARRIVE, now);
		}
		WalkTo.State state = w.walk.tick(v, now);
		if (state == WalkTo.State.UNREACHABLE) {
			w.rejected.add(pos.asLong());
			w.goal = null;
			w.walk.release(v);
			return;
		}
		if (state == WalkTo.State.ARRIVED && WalkTo.distance(v, pos) > Balance.SHELTER_DRIFT) {
			w.begun = now;
			w.walk.begin(pos, Balance.SHELTER_ARRIVE, now);
			return;
		}
		if (state == WalkTo.State.ARRIVED) v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(w.look));
	}

	private static Optional<ShelterPlanner.ShelterTarget> pick(ServerLevel level, Villager v, Set<Long> rejected) {
		Set<Long> houseBeds = new HashSet<>();
		for (long bed : Housing.beds(level.getServer()).houseBeds()) if (!rejected.contains(bed)) houseBeds.add(bed);
		Set<Long> taken = new HashSet<>();
		for (Villager other : level.getEntities(EntityType.VILLAGER, v.getBoundingBox().inflate(96), e -> e != v)) {
			Housing.home(other).ifPresent(g -> {
				if (g.dimension().equals(level.dimension())) taken.add(g.pos().asLong());
			});
		}
		Optional<Pos> home = Optional.empty();
		var mine = Housing.home(v);
		if (mine.isPresent() && mine.get().dimension().equals(level.dimension())) {
			long packed = mine.get().pos().asLong();
			if (!rejected.contains(packed)) home = Optional.of(new Pos(mine.get().pos().getX(), mine.get().pos().getY(), mine.get().pos().getZ()));
		}
		List<Pos> doors = new ArrayList<>();
		for (Buildings.BuildingView b : Buildings.all(level.getServer())) {
			if (b.level() != level || b.status() != BuildingStatus.VALID || b.type().kind() != BuildingKind.ROOM) continue;
			if (b.latestScan().isEmpty()) continue;
			Pos start = b.latestScan().get().start();
			if (start == null || rejected.contains(start.pack())) continue;
			doors.add(start);
		}
		return ShelterPlanner.choose(home, houseBeds, taken, doors, new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ()));
	}

	/** A bed is looked at on its own block. A room is looked at through its door. */
	private static BlockPos lookAt(ServerLevel level, ShelterPlanner.ShelterTarget target) {
		if (target.kind() != ShelterPlanner.Kind.ROOM) return new BlockPos(target.pos().x(), target.pos().y(), target.pos().z());
		for (Buildings.BuildingView b : Buildings.all(level.getServer())) {
			if (b.latestScan().isEmpty()) continue;
			var scan = b.latestScan().get();
			if (scan.start() == null || !scan.start().equals(target.pos()) || scan.door() == null) continue;
			return new BlockPos(scan.door().x(), scan.door().y(), scan.door().z());
		}
		return new BlockPos(target.pos().x(), target.pos().y(), target.pos().z());
	}

	private static final class Walk {
		final WalkTo walk = new WalkTo();
		final Set<Long> rejected = new HashSet<>();
		BlockPos goal;
		BlockPos look;
		long begun;
	}
}
