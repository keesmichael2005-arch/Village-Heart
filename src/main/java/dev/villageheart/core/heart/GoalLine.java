package dev.villageheart.core.heart;

import java.util.Objects;

/** One tier goal (filled by M14; the list is empty until then). */
public record GoalLine(TextLine label, int have, int need, boolean met) {
	public GoalLine { Objects.requireNonNull(label, "label"); }
}
