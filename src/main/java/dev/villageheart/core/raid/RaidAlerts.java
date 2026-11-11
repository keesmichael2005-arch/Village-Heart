package dev.villageheart.core.raid;

import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.TextLine;
import java.util.List;

/** The raid line on the Heart screen (M25). Always danger. At most two short args. */
public final class RaidAlerts {
	public static final String COMING = "gui.villageheart.heart.alert.raid_coming";
	public static final String PENDING = "gui.villageheart.heart.alert.raid_pending";
	public static final String WAVE = "gui.villageheart.heart.alert.raid_wave";
	public static final String BETWEEN = "gui.villageheart.heart.alert.raid_between";

	private RaidAlerts() {}

	/** A rolled raid that is still waiting for a player. */
	public static AlertLine pending() {
		return new AlertLine(AlertCode.RAID, Severity.DANGER, new TextLine(PENDING, "A raid is coming"));
	}

	public static AlertLine line(RaidRecord raid) {
		java.util.Objects.requireNonNull(raid, "raid");
		if (raid.wavesSpawned() <= 0) {
			return new AlertLine(AlertCode.RAID, Severity.DANGER,
					new TextLine(COMING, "Raid coming! Guards are called to duty"));
		}
		if (raid.betweenWaves()) {
			return new AlertLine(AlertCode.RAID, Severity.DANGER, new TextLine(BETWEEN,
					"Raid: wave %s beaten, the next one is coming", List.of(Integer.toString(raid.wavesSpawned()))));
		}
		return new AlertLine(AlertCode.RAID, Severity.DANGER, new TextLine(WAVE, "Raid! Wave %s, %s raiders left",
				List.of(raid.wavesSpawned() + "/" + raid.wavesTotal(), Integer.toString(raid.raidersAlive()))));
	}
}
