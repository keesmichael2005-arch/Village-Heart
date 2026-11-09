package dev.villageheart.core.sim;

/** Reads a stored report as the baseline an assertion compares with (M13): pure, the caller reads the file. */
public final class BaselineLoader {
	private BaselineLoader() {}

	/** @throws IllegalArgumentException when the text is not a report or has no day records */
	public static MetricsTable load(String reportJson) {
		MetricsJson.Report r;
		try {
			r = MetricsJson.read(reportJson);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("the baseline is not a readable report: " + e.getMessage(), e);
		}
		if (r.baseline() == null || r.days().isEmpty()) throw new IllegalArgumentException("the baseline report has no day records");
		return new MetricsTable(r.baseline(), r.days());
	}
}
