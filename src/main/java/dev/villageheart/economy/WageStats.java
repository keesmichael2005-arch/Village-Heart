package dev.villageheart.economy;

import dev.villageheart.core.economy.PayResult;
import java.util.function.Consumer;

/**
 * Wage counters for the L3 metrics ({@code wages.*}) and a test hook that sees every run. Server thread only. The collector calls
 * {@link #snapshotAndReset} once per simulated day.
 */
public final class WageStats {
	/** Counts since the last {@link #snapshotAndReset}. */
	public record Counts(long runs, long bill, long paid, long spent, long strikers) {
		public static final Counts ZERO = new Counts(0, 0, 0, 0, 0);
	}

	private static long runs, bill, paid, spent, strikers;
	private static volatile Consumer<PayResult> testListener;

	private WageStats() {}

	static void record(PayResult r) {
		runs++;
		bill += r.bill();
		paid += r.paid().size();
		spent += r.spent();
		strikers += r.unpaid().size();
		Consumer<PayResult> l = testListener;
		if (l != null) l.accept(r);
	}

	public static Counts snapshotAndReset() {
		Counts c = new Counts(runs, bill, paid, spent, strikers);
		runs = bill = paid = spent = strikers = 0;
		return c;
	}

	/** Test hook: sees every wage run. Pass null to remove. */
	public static void setTestListener(Consumer<PayResult> listener) { testListener = listener; }
}
