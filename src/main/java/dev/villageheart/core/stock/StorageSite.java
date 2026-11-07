package dev.villageheart.core.stock;

import dev.villageheart.core.room.Pos;
import java.util.Comparator;
import java.util.List;

/** One valid Storage building: its marker, the anchor a worker walks to, and its containers (sorted by position). */
public record StorageSite<K>(Pos marker, Pos anchor, List<ContainerRef<K>> containers) {
	public StorageSite {
		containers = containers.stream().sorted(Comparator.comparing(ContainerRef::pos)).toList();
	}
}
