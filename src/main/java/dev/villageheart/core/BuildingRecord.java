package dev.villageheart.core;

import java.util.Objects;

/** Placeholder; M5 extends it. */
public record BuildingRecord(String typeId, HeartLocation marker) {
	public BuildingRecord {
		Objects.requireNonNull(typeId, "typeId");
		Objects.requireNonNull(marker, "marker");
	}
}
