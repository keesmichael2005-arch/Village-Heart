package dev.villageheart.core;

/** Where the village is: a sphere of {@link Balance#CLAIM_RADIUS} blocks around the Heart (not a box). */
public final class ClaimGeometry {
	private ClaimGeometry() {}

	public static boolean inClaim(HeartLocation heart, HeartLocation pos) {
		if (!heart.dimension().equals(pos.dimension())) return false;
		long dx = pos.x() - heart.x(), dy = pos.y() - heart.y(), dz = pos.z() - heart.z();
		long r = Balance.CLAIM_RADIUS;
		return dx * dx + dy * dy + dz * dz <= r * r;
	}
}
