package dev.villageheart.core.siege;

import java.util.Optional;

/**
 * The wall-damage lines (docs/plans/M25.md 3.15). The snapshot order of the alert codes is step 15, once the codes exist.
 */
public final class SiegeAlerts {
	private SiegeAlerts() {}

	public record Line(String text, boolean danger) {}

	public static Optional<Line> line(int breached, int cracked, boolean threat, boolean hasBuilder) {
		if (breached <= 0 && cracked <= 0) return Optional.empty();
		if (breached > 0 && threat) return Optional.of(new Line("Wall breached! " + breached + " holes", true));
		String text = "Walls damaged: " + breached + " broken, " + cracked + " cracked";
		if (!hasBuilder) text = text + " (no builder)";
		return Optional.of(new Line(text, false));
	}
}
