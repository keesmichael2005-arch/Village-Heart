package dev.villageheart.core.gear;

import dev.villageheart.core.HeartLocation;
import java.util.Objects;

/** One demand slot: a camp, a need and a slot index (the index is only an identifier). */
public record OrderKey(HeartLocation building, GearNeed need, int slot) {
	public OrderKey {
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(need, "need");
		if (slot < 0) throw new IllegalArgumentException("slot < 0");
	}
}
