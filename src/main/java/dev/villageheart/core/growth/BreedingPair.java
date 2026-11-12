package dev.villageheart.core.growth;

import java.util.Objects;
import java.util.UUID;

/** Two different villagers, {@code a} the lower in {@link UUID#compareTo} order (a is the initiator of the courtship). */
public record BreedingPair(UUID a, UUID b) {
	public BreedingPair {
		Objects.requireNonNull(a, "a");
		Objects.requireNonNull(b, "b");
		if (a.equals(b)) throw new IllegalArgumentException("a pair needs two villagers");
		if (a.compareTo(b) > 0) throw new IllegalArgumentException("a must be the lower id");
	}

	public static BreedingPair of(UUID x, UUID y) { return x.compareTo(y) <= 0 ? new BreedingPair(x, y) : new BreedingPair(y, x); }

	public boolean contains(UUID id) { return a.equals(id) || b.equals(id); }

	public UUID other(UUID id) { return a.equals(id) ? b : a; }
}
