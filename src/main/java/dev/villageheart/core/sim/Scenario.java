package dev.villageheart.core.sim;

import java.util.List;

/**
 * A parsed sim/scenarios/NAME.json. {@code seed} is null unless the scenario overrides the harness seed. {@code baseline} (M13) is the path of
 * a stored report, relative to the scenario file, that {@code x@base} reads; null when the scenario compares with nothing.
 * {@code villageSiege} is the harness flag for vanilla's zombie siege (default on). {@code defended_nosiege} turns it off inside the claim.
 */
public record Scenario(String name, String description, int defaultDays, Long seed,
		List<ScenarioAction> actions, List<String> assertions, String baseline, boolean villageSiege) {
	public Scenario {
		actions = List.copyOf(actions);
		assertions = List.copyOf(assertions);
	}

	public Scenario(String name, String description, int defaultDays, Long seed, List<ScenarioAction> actions, List<String> assertions, String baseline) {
		this(name, description, defaultDays, seed, actions, assertions, baseline, true);
	}

	public Scenario(String name, String description, int defaultDays, Long seed, List<ScenarioAction> actions, List<String> assertions) {
		this(name, description, defaultDays, seed, actions, assertions, null, true);
	}
}
