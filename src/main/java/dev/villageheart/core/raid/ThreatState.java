package dev.villageheart.core.raid;

import java.util.Objects;
import java.util.Optional;

/**
 * What is threatening the village (M25). Step 2 stores the tracked raid and the last one.
 * Pending raids, sieges and wall damage are added later on this same record.
 */
public record ThreatState(Optional<RaidRecord> raid, Optional<RaidSummary> lastRaid) {
	public static final ThreatState EMPTY = new ThreatState(Optional.empty(), Optional.empty());

	public ThreatState {
		Objects.requireNonNull(raid, "raid");
		Objects.requireNonNull(lastRaid, "lastRaid");
	}

	public ThreatState withRaid(Optional<RaidRecord> raid) {
		Objects.requireNonNull(raid, "raid");
		return new ThreatState(raid, lastRaid);
	}

	public ThreatState withLastRaid(Optional<RaidSummary> lastRaid) {
		Objects.requireNonNull(lastRaid, "lastRaid");
		return new ThreatState(raid, lastRaid);
	}
}
