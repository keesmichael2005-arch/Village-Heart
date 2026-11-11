package dev.villageheart.core.siege;

import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.AlertLine;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.TextLine;
import java.util.List;
import java.util.Optional;

/**
 * The wall-damage lines (docs/plans/M25.md 3.15). {@link AlertCode#RAID} is listed before {@link AlertCode#SIEGE} and
 * {@link AlertCode#DEFENCE_DAMAGED} by the snapshot, not by moving the enum.
 */
public final class SiegeAlerts {
	private static final String ALERT = "gui.villageheart.heart.alert.";

	private SiegeAlerts() {}

	public record Line(String text, boolean danger) {}

	public static Optional<Line> line(int breached, int cracked, boolean threat, boolean hasBuilder) {
		if (breached <= 0 && cracked <= 0) return Optional.empty();
		if (breached > 0 && threat) return Optional.of(new Line("Wall breached! " + breached + " holes", true));
		String text = "Walls damaged: " + breached + " broken, " + cracked + " cracked";
		if (!hasBuilder) text = text + " (no builder)";
		return Optional.of(new Line(text, false));
	}

	/** The Overview row for the same counts. Empty when nothing is cracked or breached. */
	public static Optional<AlertLine> alert(int breached, int cracked, boolean threat, boolean hasBuilder) {
		Optional<Line> line = line(breached, cracked, threat, hasBuilder);
		if (line.isEmpty()) return Optional.empty();
		String key;
		String fallback;
		List<String> args;
		if (breached > 0 && threat) {
			key = ALERT + "defence_breached";
			fallback = "Wall breached! %s holes";
			args = List.of(Integer.toString(breached));
		} else if (!hasBuilder) {
			key = ALERT + "defence_damaged_no_builder";
			fallback = "Walls damaged: %s broken, %s cracked (no builder)";
			args = List.of(Integer.toString(breached), Integer.toString(cracked));
		} else {
			key = ALERT + "defence_damaged";
			fallback = "Walls damaged: %s broken, %s cracked";
			args = List.of(Integer.toString(breached), Integer.toString(cracked));
		}
		return Optional.of(new AlertLine(AlertCode.DEFENCE_DAMAGED, line.get().danger() ? Severity.DANGER : Severity.WARNING,
				new TextLine(key, fallback, args)));
	}
}
