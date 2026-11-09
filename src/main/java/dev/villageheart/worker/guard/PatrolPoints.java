package dev.villageheart.worker.guard;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.defence.Defences;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Where guards patrol (M18 3.8): the Heart first, then a door point for every valid room building in {@code locationOrder}, then every point
 * of every registered {@link Source} (the finished Lamp Posts and Watchtowers of M21, registered at init). Rebuilt every
 * {@link #REFRESH_TICKS} ticks.
 *
 * <p><b>Door point, not the doorway</b> (backlog-2): {@code WalkTo}'s step-aside rule never nudges a hired worker and a guard is one, so a
 * guard dwelling in a doorway would jam every worker using that door. A building's point is the first walkable cell of
 * {@code door + k * (door - start)} for k = 3, 2, 1 ({@code door} is the door's lower half, {@code start} the first air cell inside, so
 * {@code door - start} points out of the room), else the marker.
 */
public final class PatrolPoints {
	public static final int REFRESH_TICKS = 200;

	/** An extra source of patrol points (M21 defences). */
	@FunctionalInterface
	public interface Source { List<BlockPos> points(MinecraftServer server); }

	private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
	private static List<BlockPos> cache = List.of();
	private static long cachedAt = Long.MIN_VALUE / 2;
	private static int cachedEpoch = -1;

	private PatrolPoints() {}

	public static void register(Source source) {
		if (!SOURCES.contains(source)) SOURCES.add(source);
		invalidate();
	}

	/** Forces the next {@link #of} to rebuild (tests; a new claim does it by itself through the epoch). */
	public static void invalidate() { cachedAt = Long.MIN_VALUE / 2; }

	/** The patrol points of the claimed village, in patrol order; empty with no claim. */
	public static List<BlockPos> of(MinecraftServer server) {
		int epoch = VillageClaims.data(server).epoch();
		long tick = server.getTickCount();
		if (epoch != cachedEpoch || tick - cachedAt >= REFRESH_TICKS || tick < cachedAt) {
			cache = build(server);
			cachedAt = tick;
			cachedEpoch = epoch;
		}
		return cache;
	}

	private static List<BlockPos> build(MinecraftServer server) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return List.of();
		List<BlockPos> out = new ArrayList<>();
		var h = village.get().heart();
		out.add(new BlockPos(h.x(), h.y(), h.z()));
		String dimension = h.dimension();
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.type().kind() != BuildingKind.ROOM || b.status() != BuildingStatus.VALID) continue;
			if (!b.level().dimension().identifier().toString().equals(dimension)) continue;
			out.add(doorPoint(b.level(), b.marker(), b.latestScan()));
		}
		for (Source s : SOURCES) {
			for (BlockPos p : s.points(server)) if (!out.contains(p)) out.add(p);
		}
		return List.copyOf(out);
	}

	static BlockPos doorPoint(ServerLevel level, BlockPos marker, Optional<RoomScan> scan) {
		if (scan.isEmpty() || scan.get().door() == null || scan.get().start() == null) return marker;
		Pos door = scan.get().door(), start = scan.get().start();
		int dx = Integer.signum(door.x() - start.x()), dz = Integer.signum(door.z() - start.z());
		if (dx == 0 && dz == 0) return marker;
		for (int k = 3; k >= 1; k--) {
			// the ground outside a door may sit a block lower (a step) or higher than the door's own floor
			for (int dy : new int[] {0, -1, 1}) {
				Pos p = new Pos(door.x() + k * dx, door.y() + dy, door.z() + k * dz);
				if (Defences.walkable(level, p)) return new BlockPos(p.x(), p.y(), p.z());
			}
		}
		return marker;
	}
}
