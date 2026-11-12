package dev.villageheart.core.heart;

import java.util.Optional;

/**
 * The Heart row for a builder who is repairing walls. {@link ActivityKind#REPAIRING} is "Repairing".
 * A sentence that starts with "Repairs need" is the existing "Needs materials" row: the count stays on the builder's own line.
 */
public final class RepairActivity {
	private RepairActivity() {}

	public static Optional<ActivityKind> of(String line) {
		if (line == null || line.isBlank()) return Optional.empty();
		if (line.startsWith("Repairs need")) return Optional.of(ActivityKind.NEEDS_MATERIALS);
		return Optional.of(ActivityKind.REPAIRING);
	}
}
