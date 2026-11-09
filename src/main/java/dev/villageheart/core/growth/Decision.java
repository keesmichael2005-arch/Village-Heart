package dev.villageheart.core.growth;

import java.util.Optional;

public record Decision(Optional<BreedingPair> pair, Reason reason) {
	public Decision {
		if ((reason == Reason.LICENSED) != pair.isPresent()) throw new IllegalArgumentException("a pair exactly when LICENSED");
	}
}
