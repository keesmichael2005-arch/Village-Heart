package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses sim/scenarios/NAME.json. Everything that can be checked without a server is checked here. */
public final class ScenarioParser {
	private ScenarioParser() {}

	public static final class ScenarioParseException extends RuntimeException {
		public ScenarioParseException(String message) { super(message); }
	}

	/** Must match the keys of dev.villageheart.sim.SimActions (gametest source set). */
	public static final Set<String> ACTION_TYPES = Set.of("claim", "command", "fail", "crash", "hang", "site", "prefab", "field", "grove", "hire", "tier", "training_yard", "night_spawn", "wall_ring", "lamps", "move_in");

	public static Scenario parse(String fileName, String json) {
		Map<String, Object> m;
		try {
			m = Json.obj(Json.parse(json), "scenario");
		} catch (Json.JsonException e) {
			throw new ScenarioParseException(fileName + ": " + e.getMessage());
		}
		String where = fileName + ": ";
		Object schema = m.get("schema");
		if (!(schema instanceof Long s) || s != 1L) throw new ScenarioParseException(where + "schema must be 1");
		if (!(m.get("name") instanceof String name)) throw new ScenarioParseException(where + "missing name");
		if (!name.equals(fileName)) throw new ScenarioParseException(where + "name \"" + name + "\" must equal the file name");
		String description = m.get("description") instanceof String d ? d : "";
		if (!(m.get("default_days") instanceof Long dd) || dd < 1 || dd > 365) {
			throw new ScenarioParseException(where + "default_days must be an integer 1..365");
		}
		Long seed = null;
		if (m.containsKey("seed")) {
			if (!(m.get("seed") instanceof Long sd)) throw new ScenarioParseException(where + "seed must be an integer");
			seed = sd;
		}
		List<ScenarioAction> actions = new ArrayList<>();
		Object rawActions = m.getOrDefault("actions", List.of());
		if (!(rawActions instanceof List<?> la)) throw new ScenarioParseException(where + "actions must be an array");
		int i = 0;
		for (Object o : la) {
			String at = where + "actions[" + i++ + "]: ";
			if (!(o instanceof Map<?, ?> am)) throw new ScenarioParseException(at + "must be an object");
			if (!(am.get("action") instanceof String type)) throw new ScenarioParseException(at + "missing \"action\"");
			if (!ACTION_TYPES.contains(type)) throw new ScenarioParseException(at + "unknown action \"" + type + "\" (known: " + new java.util.TreeSet<>(ACTION_TYPES) + ")");
			int day = intField(am, "day", 0, at);
			int tick = intField(am, "tick", 0, at);
			if (day < 0) throw new ScenarioParseException(at + "day must be >= 0");
			if (tick < 0 || tick > 23999) throw new ScenarioParseException(at + "tick must be 0..23999");
			Map<String, Object> fields = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : am.entrySet()) {
				String k = (String) e.getKey();
				if (!k.equals("action") && !k.equals("day") && !k.equals("tick")) fields.put(k, e.getValue());
			}
			if (type.equals("command") && !(fields.get("command") instanceof String)) {
				throw new ScenarioParseException(at + "command action needs a string \"command\"");
			}
			try {
				ActionSpecs.validate(type, fields);
			} catch (IllegalArgumentException e) {
				throw new ScenarioParseException(at + type + ": " + e.getMessage());
			}
			actions.add(new ScenarioAction(type, day, tick, fields));
		}
		List<String> assertions = new ArrayList<>();
		Object rawAs = m.getOrDefault("assertions", List.of());
		if (!(rawAs instanceof List<?> as)) throw new ScenarioParseException(where + "assertions must be an array");
		for (Object o : as) {
			if (!(o instanceof String a)) throw new ScenarioParseException(where + "assertions must be strings");
			try {
				Assertion.parse(a);
			} catch (AssertionSyntaxException e) {
				throw new ScenarioParseException(where + e.getMessage());
			}
			assertions.add(a);
		}
		String baseline = null;
		if (m.containsKey("baseline")) {
			if (!(m.get("baseline") instanceof String b) || b.isBlank()) throw new ScenarioParseException(where + "baseline must be a file path");
			baseline = b;
		}
		boolean usesBase = assertions.stream().anyMatch(a -> a.contains("@base"));
		if (usesBase && baseline == null) throw new ScenarioParseException(where + "an assertion uses @base but the scenario has no \"baseline\"");
		boolean villageSiege = true;
		if (m.containsKey("village_siege")) {
			if (!(m.get("village_siege") instanceof Boolean b)) throw new ScenarioParseException(where + "village_siege must be true or false");
			villageSiege = b;
		}
		return new Scenario(name, description, dd.intValue(), seed, actions, assertions, baseline, villageSiege);
	}

	private static int intField(Map<?, ?> m, String k, int dflt, String at) {
		Object o = m.get(k);
		if (o == null) return dflt;
		if (!(o instanceof Long l) || l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) throw new ScenarioParseException(at + k + " must be an integer");
		return l.intValue();
	}
}
