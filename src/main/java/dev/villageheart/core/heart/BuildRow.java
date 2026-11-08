package dev.villageheart.core.heart;

import dev.villageheart.core.blueprint.BuildStatus;
import java.util.List;
import java.util.Objects;

/**
 * One build of the Buildings tab (M20): which blueprint, where, how far, what it is doing, what it is short of and who builds it.
 *
 * @param state       the status code (the colour and the icon of the row)
 * @param placed      PLACE steps done
 * @param total       PLACE steps in all
 * @param status      the status line ("Building: 50%", "Needs 12 more planks")
 * @param missing     at most {@link HeartLimits#MAX_BUILD_MISSING} lines of what the build is still short of, first one first
 * @param builderName "" when no builder is working on it
 */
public record BuildRow(String blueprintId, int x, int y, int z, BuildStatus state, int placed, int total, TextLine status,
		List<TextLine> missing, String builderName) {
	public BuildRow {
		Objects.requireNonNull(blueprintId, "blueprintId");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(builderName, "builderName");
		missing = List.copyOf(missing);
	}
}
