package dev.villageheart.core.sim;

import java.util.Map;

/** One scenario action: a type id, when it runs (day 0 = during setup), and its type-specific fields. */
public record ScenarioAction(String type, int day, int tick, Map<String, Object> fields) {
	public ScenarioAction {
		fields = Map.copyOf(fields);
	}

	/** Ticks since the baseline at which this action is due (day >= 1 only). */
	public long dueTick() {
		return (long) (day - 1) * 24000L + tick;
	}
}
