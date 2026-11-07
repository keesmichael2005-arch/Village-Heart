package dev.villageheart.core;

import java.util.Objects;

/** Where a Village Heart is: dimension id (e.g. "minecraft:overworld") plus block coordinates. */
public record HeartLocation(String dimension, int x, int y, int z) {
	public HeartLocation {
		Objects.requireNonNull(dimension, "dimension");
		if (dimension.isBlank()) throw new IllegalArgumentException("dimension must not be blank");
	}
}
