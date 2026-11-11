package dev.villageheart.core.raid;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where a worker goes when a raid or a siege starts (M25 plan 3.2 #7). Own House bed, else the nearest free House bed,
 * else the nearest valid room's inside door. Ties break by squared distance, then x, then z, then y.
 */
public final class ShelterPlanner {
	private ShelterPlanner() {}

	public enum Kind { OWN_BED, HOUSE_BED, ROOM }

	public record ShelterTarget(Pos pos, Kind kind) {}

	public static Optional<ShelterTarget> choose(Optional<Pos> home, Set<Long> houseBeds, Set<Long> takenBeds, List<Pos> roomDoorsInside, Pos me) {
		if (home.isPresent() && houseBeds.contains(home.get().pack())) return Optional.of(new ShelterTarget(home.get(), Kind.OWN_BED));
		Pos bestBed = null;
		for (long packed : houseBeds) {
			if (takenBeds.contains(packed) && (home.isEmpty() || home.get().pack() != packed)) continue;
			Pos bed = Pos.unpack(packed);
			if (bestBed == null || better(bed, bestBed, me) < 0) bestBed = bed;
		}
		if (bestBed != null) return Optional.of(new ShelterTarget(bestBed, Kind.HOUSE_BED));
		Pos bestDoor = null;
		for (Pos door : roomDoorsInside) {
			if (bestDoor == null || better(door, bestDoor, me) < 0) bestDoor = door;
		}
		return bestDoor == null ? Optional.empty() : Optional.of(new ShelterTarget(bestDoor, Kind.ROOM));
	}

	/** Negative when {@code a} is the better shelter. */
	private static int better(Pos a, Pos b, Pos me) {
		int byDistance = Long.compare(dist2(me, a), dist2(me, b));
		if (byDistance != 0) return byDistance;
		int byX = Integer.compare(a.x(), b.x());
		if (byX != 0) return byX;
		int byZ = Integer.compare(a.z(), b.z());
		return byZ != 0 ? byZ : Integer.compare(a.y(), b.y());
	}

	private static long dist2(Pos a, Pos b) {
		long dx = (long) a.x() - b.x();
		long dy = (long) a.y() - b.y();
		long dz = (long) a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
