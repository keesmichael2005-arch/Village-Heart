package dev.villageheart.core.heart;

import java.util.List;
import java.util.Objects;

/** @param moreReasons reasons beyond the {@link HeartLimits#MAX_REASONS} listed */
public record BuildingRow(String typeId, int x, int y, int z, RowStatus status, List<TextLine> reasons, int moreReasons,
		DetailKind detail, int detailCount, int workersHere) {
	public BuildingRow {
		Objects.requireNonNull(typeId, "typeId");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(detail, "detail");
		reasons = List.copyOf(reasons);
	}
}
