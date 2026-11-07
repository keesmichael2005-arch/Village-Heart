package dev.villageheart.core;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Holder of the "one village per world" rule. Mutable; owned by the saved data. */
public final class WorldClaim {
	public enum BreakDecision { ALLOWED_UNCLAIMED, ALLOWED_OWNER, DENIED_NOT_OWNER }

	private VillageState village;

	public WorldClaim() {}

	public WorldClaim(Optional<VillageState> village) {
		this.village = village.orElse(null);
	}

	public Optional<VillageState> village() {
		return Optional.ofNullable(village);
	}

	public ClaimRules.Result evaluate(double distanceToBell) {
		return ClaimRules.evaluate(distanceToBell, village != null);
	}

	public ClaimRules.Result claim(HeartLocation at, UUID owner, String ownerName, double distanceToBell, long gameTime) {
		ClaimRules.Result r = evaluate(distanceToBell);
		if (r == ClaimRules.Result.CLAIMED) village = VillageState.founded(at, owner, ownerName, gameTime);
		return r;
	}

	public Optional<VillageState> releaseAt(HeartLocation at) {
		if (village == null || !village.isHeartAt(at)) return Optional.empty();
		VillageState released = village;
		village = null;
		return Optional.of(released);
	}

	public BreakDecision mayBreakHeart(HeartLocation at, UUID player) {
		if (village == null || !village.isHeartAt(at)) return BreakDecision.ALLOWED_UNCLAIMED;
		return village.owner().equals(player) ? BreakDecision.ALLOWED_OWNER : BreakDecision.DENIED_NOT_OWNER;
	}

	public void update(UnaryOperator<VillageState> f) {
		if (village == null) throw new IllegalStateException("no village");
		village = Objects.requireNonNull(f.apply(village));
	}

	public void clear() {
		village = null;
	}

	@Override public boolean equals(Object o) {
		return o instanceof WorldClaim w && Objects.equals(village, w.village);
	}

	@Override public int hashCode() {
		return Objects.hashCode(village);
	}
}
