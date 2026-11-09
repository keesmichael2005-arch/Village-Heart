package dev.villageheart.needs;

import dev.villageheart.core.WorkerRecord;
import java.util.function.Predicate;

/** Whether a worker is paid. Stubbed true until M12 (the treasury) swaps the source. */
public final class PaidSource {
	public static final Predicate<WorkerRecord> ALWAYS = r -> true;
	private static volatile Predicate<WorkerRecord> current = ALWAYS;

	private PaidSource() {}

	public static Predicate<WorkerRecord> current() { return current; }

	public static void set(Predicate<WorkerRecord> source) { current = source == null ? ALWAYS : source; }
}
