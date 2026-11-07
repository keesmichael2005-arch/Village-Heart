package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The request file scripts/simulate.sh writes for the harness. */
public record SimRequest(String scenarioFile, String scenario, int days, String out, List<String> extraAssertions,
		Integer stopAfterDay, boolean resume) {

	public SimRequest {
		extraAssertions = List.copyOf(extraAssertions);
	}

	public static SimRequest parse(String json) {
		Map<String, Object> m = Json.obj(Json.parse(json), "request");
		List<String> extra = new ArrayList<>();
		for (Object o : Json.arr(m.getOrDefault("extra_assertions", List.of()), "extra_assertions")) {
			if (!(o instanceof String s)) throw new Json.JsonException("extra_assertions must be strings");
			extra.add(s);
		}
		Object stop = m.get("stop_after_day");
		if (stop != null && !(stop instanceof Long)) throw new Json.JsonException("stop_after_day must be an integer or null");
		return new SimRequest(str(m, "scenario_file"), str(m, "scenario"), num(m, "days"), str(m, "out"), extra,
				stop == null ? null : ((Long) stop).intValue(), Boolean.TRUE.equals(m.get("resume")));
	}

	private static String str(Map<String, Object> m, String k) {
		if (!(m.get(k) instanceof String s)) throw new Json.JsonException("request needs string \"" + k + "\"");
		return s;
	}

	private static int num(Map<String, Object> m, String k) {
		if (!(m.get(k) instanceof Long l)) throw new Json.JsonException("request needs integer \"" + k + "\"");
		return l.intValue();
	}
}
