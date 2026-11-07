package dev.villageheart.core.sim;

import java.util.List;

public record ScenarioResult(SimStatus status, List<AssertionResult> results) {
	public ScenarioResult {
		results = List.copyOf(results);
	}
}
