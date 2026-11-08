package dev.villageheart.building;

import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.rank.ContainerGuard;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * What later milestones read: the registered buildings with their last verdict and cached scan. A valid building with no
 * cached scan (right after a restart, or unloaded) is "valid, contents unknown": consumers skip it.
 */
public final class Buildings {
	private Buildings() {}

	public record BuildingView(BuildingType type, ServerLevel level, BlockPos marker, BuildingStatus status,
			@Nullable BlockPos link, Optional<RoomScan> latestScan, Optional<Verdict> verdict) {}

	public static List<BuildingView> all(MinecraftServer server) {
		List<BuildingView> out = new ArrayList<>();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return out;
		BuildingRuntime rt = BuildingSystem.runtime(server);
		for (BuildingRecord b : village.get().buildings()) {
			Optional<BuildingType> type = b.type();
			ServerLevel level = BuildingSystem.levelOf(server, b.marker());
			if (type.isEmpty() || level == null) continue;
			HeartLocation m = b.marker(), l = b.link();
			out.add(new BuildingView(type.get(), level, new BlockPos(m.x(), m.y(), m.z()), b.status(),
					l == null ? null : new BlockPos(l.x(), l.y(), l.z()),
					Optional.ofNullable(rt.rawScans.get(m)), Optional.ofNullable(rt.verdicts.get(m))));
		}
		return out;
	}

	/**
	 * Every registered ROOM building of the village for the Visitor container rule (M24). Taken from the saved registry, never
	 * from the scan cache: after a restart the cache is empty and every room is then a fail-closed box around its marker.
	 */
	public static List<ContainerGuard.GuardArea> guardAreas(MinecraftServer server) {
		List<ContainerGuard.GuardArea> out = new ArrayList<>();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return out;
		BuildingRuntime rt = BuildingSystem.runtime(server);
		for (BuildingRecord b : village.get().buildings()) {
			Optional<BuildingType> type = b.type();
			if (type.isEmpty() || type.get().kind() != BuildingKind.ROOM) continue;
			HeartLocation m = b.marker();
			out.add(new ContainerGuard.GuardArea(m.dimension(), new Pos(m.x(), m.y(), m.z()), Optional.ofNullable(rt.lastEnclosed.get(m))));
		}
		return out;
	}

	public static List<BuildingView> valid(MinecraftServer server, BuildingType type) {
		return all(server).stream().filter(v -> v.type() == type && v.status() == BuildingStatus.VALID).toList();
	}

	/** Prioritise every building (M14 calls this after a tier-up so gated markers turn green within a few ticks). */
	public static void requestAll(MinecraftServer server) {
		VillageClaims.current(server).ifPresent(v -> {
			BuildingRuntime rt = BuildingSystem.runtime(server);
			for (BuildingRecord b : v.buildings()) rt.schedule.prioritise(b.marker());
		});
	}

	/** Prioritise one building (the Heart screen's "check now"). */
	public static void request(MinecraftServer server, HeartLocation loc) {
		if (VillageClaims.current(server).flatMap(v -> v.building(loc)).isPresent()) BuildingSystem.runtime(server).schedule.prioritise(loc);
	}

	/**
	 * The deepest point of a Mineshaft at its last complete survey, or empty when it has not been surveyed since the restart (M16: a miner
	 * does not go down a shaft whose depth is unknown).
	 */
	public static Optional<dev.villageheart.core.building.ShaftScanner.ShaftDepth> shaftDepth(MinecraftServer server, HeartLocation loc) {
		return Optional.ofNullable(BuildingSystem.runtime(server).shaftDeepest.get(loc));
	}

	/** The latest verdict of a building, without checking. */
	public static Optional<Verdict> report(MinecraftServer server, HeartLocation loc) {
		return Optional.ofNullable(BuildingSystem.runtime(server).verdicts.get(loc));
	}
}
