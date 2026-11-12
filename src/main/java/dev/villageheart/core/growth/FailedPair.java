package dev.villageheart.core.growth;

import java.util.Objects;

/** A pair whose licence lapsed with CANNOT_MEET on {@code day}. */
public record FailedPair(BreedingPair pair, long day) {
	public FailedPair { Objects.requireNonNull(pair, "pair"); }
}
