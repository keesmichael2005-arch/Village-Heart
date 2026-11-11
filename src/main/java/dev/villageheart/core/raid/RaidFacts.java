package dev.villageheart.core.raid;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Objects;

/**
 * One look at a vanilla raid (M25). Pure: the glue fills it, {@link RaidTracker} decides.
 * A raid is relevant when it is still going and its centre or a raider is inside the claim.
 */
public record RaidFacts(boolean present, int vanillaId, boolean over, boolean stopped, boolean victory, boolean loss,
		boolean centreInClaim, int raidersInClaim, int raidersAlive, int wavesSpawned, int wavesTotal,
		boolean betweenWaves, boolean started) {
	public static final RaidFacts NONE = new RaidFacts(false, -1, false, false, false, false, false, 0, 0, 0, 0, false, false);

	/**
	 * Waves the raid will actually run: the saved group count, plus the omen bonus wave when the omen is above 1.
	 * A capped raid already has its group count cut, so an omen of 1 stays at that count.
	 */
	public static int wavesTotal(int numGroups, int omen) {
		int groups = Math.max(0, numGroups);
		return groups + (omen > 1 ? 1 : 0);
	}

	/**
	 * Facts for a raid that exists. {@code centreInClaim} and {@code raidersInClaim} use the claim sphere
	 * ({@link dev.villageheart.core.Balance#CLAIM_RADIUS}, the edge counts as inside).
	 */
	public static RaidFacts from(int vanillaId, boolean over, boolean stopped, boolean victory, boolean loss, Pos centre,
			List<Pos> raiders, HeartLocation heart, int raidersAlive, int wavesSpawned, int wavesTotal, boolean betweenWaves,
			boolean started) {
		Objects.requireNonNull(raiders, "raiders");
		Objects.requireNonNull(heart, "heart");
		int inside = 0;
		for (Pos p : raiders) if (p != null && inClaim(heart, p)) inside++;
		boolean centreIn = centre != null && inClaim(heart, centre);
		return new RaidFacts(true, vanillaId, over, stopped, victory, loss, centreIn, inside, raidersAlive, wavesSpawned,
				wavesTotal, betweenWaves, started);
	}

	/** Still a raid this village should track. An over or stopped raid that was never tracked does not start. */
	public boolean relevant() {
		return present && !over && !stopped && !victory && !loss && (centreInClaim || raidersInClaim > 0);
	}

	private static boolean inClaim(HeartLocation heart, Pos p) {
		return ClaimGeometry.inClaim(heart, new HeartLocation(heart.dimension(), p.x(), p.y(), p.z()));
	}
}
