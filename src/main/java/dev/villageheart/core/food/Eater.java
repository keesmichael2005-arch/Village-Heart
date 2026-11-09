package dev.villageheart.core.food;

import dev.villageheart.core.Balance;
import java.util.UUID;

/** One villager at dinner. {@code workerIndex} is the hiring order, -1 for a villager without a job. */
public record Eater(UUID id, boolean child, int hungryDays, int workerIndex) {
	public int need() { return child ? Balance.CHILD_NUTRITION : Balance.ADULT_NUTRITION; }
}
