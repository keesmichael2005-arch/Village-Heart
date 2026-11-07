package dev.villageheart.core;

import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * A registered building: its type, marker, the last verdict and (for outdoor sites) the parent marker it is linked to.
 * Reasons are not saved; they are recomputed on the next check.
 */
public record BuildingRecord(String typeId, HeartLocation marker, BuildingStatus status, @Nullable HeartLocation link) {
	public BuildingRecord {
		Objects.requireNonNull(typeId, "typeId");
		Objects.requireNonNull(marker, "marker");
		Objects.requireNonNull(status, "status");
	}

	public BuildingRecord(String typeId, HeartLocation marker) {
		this(typeId, marker, BuildingStatus.UNCHECKED, null);
	}

	public BuildingRecord withStatus(BuildingStatus s) { return new BuildingRecord(typeId, marker, s, link); }

	public BuildingRecord withLink(@Nullable HeartLocation l) { return new BuildingRecord(typeId, marker, status, l); }

	public Optional<BuildingType> type() { return BuildingType.byId(typeId); }
}
