package dev.villageheart.core.siege;

import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.core.raid.RaidSummary;
import java.util.Optional;

/**
 * Saved threat book of the village (docs/plans/M25.md 3.4): the tracked raid, a pending random raid, the running siege,
 * the day counters, and wall damage. Day counters use {@link #NO_DAY} when nothing has happened yet.
 * The raid records are the plan's shape; track A owns the tracker that fills them.
 */
public record ThreatState(Optional<RaidRecord> raid, Optional<RaidSummary> lastRaid, Optional<PendingRaid> pending,
		Optional<SiegeRecord> siege, Optional<SiegeRecord> lastSiege, 		int lastRaidEndDay, int lastSiegeDay, int lastRewardDay,
		int lastRaidRollDay, int lastSiegeRollDay, int pendingSiegeDay, DefenceDamage damage) {
	public static final int NO_DAY = -1;

	public static final ThreatState EMPTY = new ThreatState(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
			Optional.empty(), NO_DAY, NO_DAY, NO_DAY, NO_DAY, NO_DAY, NO_DAY, DefenceDamage.EMPTY);

	public ThreatState {
		raid = raid == null ? Optional.empty() : raid;
		lastRaid = lastRaid == null ? Optional.empty() : lastRaid;
		pending = pending == null ? Optional.empty() : pending;
		siege = siege == null ? Optional.empty() : siege;
		lastSiege = lastSiege == null ? Optional.empty() : lastSiege;
		if (damage == null) damage = DefenceDamage.EMPTY;
	}

	/** A raid is tracked or a siege is running. Workers shelter on this; repairs do not. */
	public boolean underAttack() { return raid.isPresent() || siege.isPresent(); }

	public ThreatState withRaid(Optional<RaidRecord> raid) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withLastRaid(Optional<RaidSummary> lastRaid) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withPending(Optional<PendingRaid> pending) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withSiege(Optional<SiegeRecord> siege) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withDamage(DefenceDamage damage) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withLastRaidEndDay(int day) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, day, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withLastRewardDay(int day) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, day, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withRollDays(int raidRollDay, int siegeRollDay) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, raidRollDay, siegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withLastSiege(Optional<SiegeRecord> lastSiege) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	public ThreatState withLastSiegeDay(int day) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, day, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, pendingSiegeDay, damage);
	}

	/** The day a siege was rolled and has not spawned yet. {@link #NO_DAY} means nothing is owed. */
	public ThreatState withPendingSiegeDay(int day) {
		return new ThreatState(raid, lastRaid, pending, siege, lastSiege, lastRaidEndDay, lastSiegeDay, lastRewardDay, lastRaidRollDay, lastSiegeRollDay, day, damage);
	}
}
