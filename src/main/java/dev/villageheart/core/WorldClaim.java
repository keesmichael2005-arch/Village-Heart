package dev.villageheart.core;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Holder of the "one village per world" rule. Mutable; owned by the saved data. */
public final class WorldClaim {
	public enum BreakDecision { ALLOWED_UNCLAIMED, ALLOWED_OWNER, DENIED_NOT_OWNER }

	private VillageState village;
	/** Job blocks whose POI ticket the mod holds (M7). Belongs to the village: cleared with it. */
	private final Set<HeartLocation> reservedJobBlocks = new LinkedHashSet<>();

	/** Held job blocks whose ticket could not be released (chunk unloaded when the village was released): finished when they load. */
	private final Set<HeartLocation> pendingTicketReleases = new LinkedHashSet<>();

	/** The day index of the last dinner (M11); belongs to the village, so a new claim starts without one. */
	private Long lastDinnerDay;

	/** Who is paid and who strikes today (M12); absent for a new claim or an old save, initialised on the first tick. Belongs to the village. */
	private dev.villageheart.core.economy.Payroll payroll;

	/** The market's reserve settings and today's sales (M22); belongs to the village, so a new claim starts with the defaults. */
	private dev.villageheart.core.market.MarketState market = dev.villageheart.core.market.MarketState.initial();

	public WorldClaim() {}

	public WorldClaim(Optional<VillageState> village) {
		this.village = village.orElse(null);
	}

	public WorldClaim(Optional<VillageState> village, Set<HeartLocation> reservedJobBlocks) {
		this(village);
		this.reservedJobBlocks.addAll(reservedJobBlocks);
	}

	public WorldClaim(Optional<VillageState> village, Set<HeartLocation> reservedJobBlocks, Set<HeartLocation> pendingTicketReleases) {
		this(village, reservedJobBlocks);
		this.pendingTicketReleases.addAll(pendingTicketReleases);
	}

	public WorldClaim(Optional<VillageState> village, Set<HeartLocation> reservedJobBlocks, Set<HeartLocation> pendingTicketReleases,
			java.util.OptionalLong lastDinnerDay) {
		this(village, reservedJobBlocks, pendingTicketReleases);
		this.lastDinnerDay = lastDinnerDay.isPresent() ? lastDinnerDay.getAsLong() : null;
	}

	public WorldClaim(Optional<VillageState> village, Set<HeartLocation> reservedJobBlocks, Set<HeartLocation> pendingTicketReleases,
			java.util.OptionalLong lastDinnerDay, Optional<dev.villageheart.core.economy.Payroll> payroll) {
		this(village, reservedJobBlocks, pendingTicketReleases, lastDinnerDay);
		this.payroll = payroll.orElse(null);
	}

	public WorldClaim(Optional<VillageState> village, Set<HeartLocation> reservedJobBlocks, Set<HeartLocation> pendingTicketReleases,
			java.util.OptionalLong lastDinnerDay, Optional<dev.villageheart.core.economy.Payroll> payroll, dev.villageheart.core.market.MarketState market) {
		this(village, reservedJobBlocks, pendingTicketReleases, lastDinnerDay, payroll);
		this.market = Objects.requireNonNull(market, "market");
	}

	public dev.villageheart.core.market.MarketState market() { return market; }

	public void setMarket(dev.villageheart.core.market.MarketState m) { market = Objects.requireNonNull(m, "market"); }

	public Optional<dev.villageheart.core.economy.Payroll> payroll() { return Optional.ofNullable(payroll); }

	public void setPayroll(dev.villageheart.core.economy.Payroll p) { payroll = Objects.requireNonNull(p, "payroll"); }

	public java.util.OptionalLong lastDinnerDay() { return lastDinnerDay == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(lastDinnerDay); }

	public void setLastDinnerDay(long day) { lastDinnerDay = day; }

	/** A copy of the pending releases. Unlike the reservations they survive {@link #releaseAt} and {@link #clear}. */
	public Set<HeartLocation> pendingTicketReleases() { return new LinkedHashSet<>(pendingTicketReleases); }

	public void setPendingTicketReleases(Set<HeartLocation> blocks) {
		pendingTicketReleases.clear();
		pendingTicketReleases.addAll(blocks);
	}

	/** A copy; use {@link #setReservedJobBlocks} to change. */
	public Set<HeartLocation> reservedJobBlocks() { return new LinkedHashSet<>(reservedJobBlocks); }

	public void setReservedJobBlocks(Set<HeartLocation> blocks) {
		reservedJobBlocks.clear();
		reservedJobBlocks.addAll(blocks);
	}

	public Optional<VillageState> village() {
		return Optional.ofNullable(village);
	}

	public ClaimRules.Result evaluate(double distanceToBell) {
		return ClaimRules.evaluate(distanceToBell, village != null);
	}

	public ClaimRules.Result claim(HeartLocation at, UUID owner, String ownerName, double distanceToBell, long gameTime) {
		ClaimRules.Result r = evaluate(distanceToBell);
		if (r == ClaimRules.Result.CLAIMED) {
			village = VillageState.founded(at, owner, ownerName, gameTime);
			lastDinnerDay = null;
			payroll = null;
			market = dev.villageheart.core.market.MarketState.initial();
		}
		return r;
	}

	public Optional<VillageState> releaseAt(HeartLocation at) {
		if (village == null || !village.isHeartAt(at)) return Optional.empty();
		VillageState released = village;
		village = null;
		reservedJobBlocks.clear();
		lastDinnerDay = null;
		payroll = null;
		market = dev.villageheart.core.market.MarketState.initial();
		return Optional.of(released);
	}

	public BreakDecision mayBreakHeart(HeartLocation at, UUID player) {
		if (village == null || !village.isHeartAt(at)) return BreakDecision.ALLOWED_UNCLAIMED;
		boolean allowed = dev.villageheart.core.heart.Permissions.allows(village.rankOf(player),
				dev.villageheart.core.heart.Permission.BREAK_HEART);
		return allowed ? BreakDecision.ALLOWED_OWNER : BreakDecision.DENIED_NOT_OWNER;
	}

	public void update(UnaryOperator<VillageState> f) {
		if (village == null) throw new IllegalStateException("no village");
		village = Objects.requireNonNull(f.apply(village));
	}

	public void clear() {
		village = null;
		reservedJobBlocks.clear();
		lastDinnerDay = null;
		payroll = null;
		market = dev.villageheart.core.market.MarketState.initial();
	}

	@Override public boolean equals(Object o) {
		return o instanceof WorldClaim w && Objects.equals(village, w.village) && reservedJobBlocks.equals(w.reservedJobBlocks)
				&& pendingTicketReleases.equals(w.pendingTicketReleases) && Objects.equals(lastDinnerDay, w.lastDinnerDay)
				&& Objects.equals(payroll, w.payroll) && market.equals(w.market);
	}

	@Override public int hashCode() {
		return Objects.hash(village, reservedJobBlocks);
	}
}
