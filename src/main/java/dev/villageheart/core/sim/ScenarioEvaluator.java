package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.List;

public final class ScenarioEvaluator {
	private ScenarioEvaluator() {}

	/** Parses the scenario's assertions followed by the extras. Throws AssertionSyntaxException on the first bad one. */
	public static List<Assertion> parseAll(Scenario scenario, List<String> extra) {
		List<Assertion> out = new ArrayList<>();
		for (String s : scenario.assertions()) out.add(Assertion.parse(s));
		for (String s : extra) out.add(Assertion.parse(s));
		return out;
	}

	public static ScenarioResult evaluate(Scenario scenario, MetricsTable table, List<String> extra) {
		return evaluate(parseAll(scenario, extra), table);
	}

	public static ScenarioResult evaluate(List<Assertion> assertions, MetricsTable table) {
		if (table.lastDay() == 0) {
			return new ScenarioResult(SimStatus.FAIL,
					List.of(new AssertionResult("run produced day records", false, "no day records")));
		}
		List<AssertionResult> results = new ArrayList<>();
		boolean ok = true;
		for (Assertion a : assertions) {
			AssertionResult r = a.evaluate(table);
			ok &= r.passed();
			results.add(r);
		}
		return new ScenarioResult(ok ? SimStatus.PASS : SimStatus.FAIL, results);
	}
}
