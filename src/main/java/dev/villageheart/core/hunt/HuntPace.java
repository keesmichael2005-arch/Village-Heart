package dev.villageheart.core.hunt;

import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.worker.Needs;

/**
 * Units per tick of an away hunter. Not {@link WorkSpeed#multiplier} with the tool tier: a bow and a diamond sword must give the same
 * trip length (DESIGN: 3 hours, 2 trips a day), so only the level and the needs count. No weapon: 0.
 */
public final class HuntPace {
	private HuntPace() {}

	public static double multiplier(boolean hasWeapon, int level, Needs needs) {
		return hasWeapon ? WorkSpeed.untooled(level, needs) : 0.0;
	}
}
