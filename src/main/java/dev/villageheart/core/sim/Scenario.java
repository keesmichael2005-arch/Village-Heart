package dev.villageheart.core.sim;

import java.util.List;

/** A parsed sim/scenarios/NAME.json. {@code seed} is null unless the scenario overrides the harness seed. */
public record Scenario(String name, String description, int defaultDays, Long seed,
		List<ScenarioAction> actions, List<String> assertions) {
	public Scenario {
		actions = List.copyOf(actions);
		assertions = List.copyOf(assertions);
	}
}
