package dev.villageheart.core.building;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The claim shape: a 3-D Euclidean sphere around the Heart, inclusive (DESIGN section 1). Only the marker position is
 * checked. The Mineshaft and the Hunting Ground may be farther out than the rest.
 */
public final class ClaimArea {
	private ClaimArea() {}

	public static int maxDistance(BuildingType t) {
		return t == BuildingType.MINESHAFT || t == BuildingType.HUNTING_GROUND ? Balance.OUTDOOR_SITE_RADIUS : Balance.CLAIM_RADIUS;
	}

	/** True when {@code marker} is in the claim sphere of radius {@code max} around {@code heart} (same dimension assumed). */
	public static boolean within(HeartLocation heart, HeartLocation marker, int max) {
		return distanceSquared(heart, marker) <= (long) max * max;
	}

	/** Null when the marker is inside the claim, otherwise the reason (OTHER_DIMENSION or OUTSIDE_CLAIM). */
	@Nullable
	public static Reason check(HeartLocation heart, HeartLocation marker, BuildingType t) {
		if (!heart.dimension().equals(marker.dimension())) return Reason.otherDimension();
		int max = maxDistance(t);
		if (within(heart, marker, max)) return null;
		return Reason.outsideClaim((int) Math.ceil(Math.sqrt((double) distanceSquared(heart, marker))), max);
	}

	private static long distanceSquared(HeartLocation a, HeartLocation b) {
		long dx = (long) a.x() - b.x(), dy = (long) a.y() - b.y(), dz = (long) a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
