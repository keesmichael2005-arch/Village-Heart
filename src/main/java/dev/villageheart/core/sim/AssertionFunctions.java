package dev.villageheart.core.sim;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/** The aggregate functions of the assertion language. Add new ones here, each with a unit test. */
public final class AssertionFunctions {
	private AssertionFunctions() {}

	private static final Map<String, ToDoubleFunction<double[]>> FUNCS = Map.of(
			"sum", v -> { double s = 0; for (double x : v) s += x; return s; },
			"mean", v -> { double s = 0; for (double x : v) s += x; return s / v.length; },
			"min", v -> { double m = Double.POSITIVE_INFINITY; for (double x : v) m = Math.min(m, x); return m; },
			"max", v -> { double m = Double.NEGATIVE_INFINITY; for (double x : v) m = Math.max(m, x); return m; });

	public static boolean isFunction(String name) {
		return FUNCS.containsKey(name);
	}

	/** Folds a non-empty list of values. */
	public static double apply(String name, double[] values) {
		if (values.length == 0) throw new IllegalArgumentException("no values to fold");
		return FUNCS.get(name).applyAsDouble(values);
	}
}
