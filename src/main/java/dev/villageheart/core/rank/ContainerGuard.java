package dev.villageheart.core.rank;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import java.util.List;
import java.util.Optional;

/**
 * Which positions count as "inside a building of the village" for the Visitor container rule (M24).
 * Pure: the glue builds the {@link GuardArea} list from the saved building registry and the runtime scan cache.
 */
public final class ContainerGuard {
	private ContainerGuard() {}

	/**
	 * One registered room building.
	 *
	 * @param enclosed the last <em>enclosed</em> scan of the room (never a leaking one); empty until the first one
	 */
	public record GuardArea(String dimension, Pos marker, Optional<RoomScan> enclosed) {}

	/**
	 * True when some area in the same dimension has the position in its enclosed scan (interior or shell), or has no
	 * enclosed scan yet and the position is within {@link Balance#GUARD_FALLBACK_RADIUS} blocks (Chebyshev) of its
	 * marker: fail closed until the first scan.
	 */
	public static boolean isProtected(String dimension, Pos pos, List<GuardArea> areas) {
		for (GuardArea a : areas) {
			if (!a.dimension().equals(dimension)) continue;
			if (a.enclosed().isPresent()) {
				if (a.enclosed().get().occupies(pos)) return true;
			} else if (chebyshev(a.marker(), pos) <= Balance.GUARD_FALLBACK_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** True when the position is within the fallback box of a registered marker (used for the marker blocks themselves). */
	public static boolean isMarker(String dimension, Pos pos, List<GuardArea> areas) {
		for (GuardArea a : areas) if (a.dimension().equals(dimension) && a.marker().equals(pos)) return true;
		return false;
	}

	private static int chebyshev(Pos a, Pos b) {
		return Math.max(Math.abs(a.x() - b.x()), Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z())));
	}
}
