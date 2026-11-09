package dev.villageheart.core.mine;

import dev.villageheart.core.job.ToolTier;
import java.util.Objects;
import java.util.Optional;

/** One line of a haul table. {@code chance} 1.0 is a fixed entry; {@code minTier} gates a chance entry on the pickaxe. */
public record HaulEntry(String itemId, int count, double chance, Optional<ToolTier> minTier) {
	public HaulEntry {
		Objects.requireNonNull(itemId, "itemId");
		Objects.requireNonNull(minTier, "minTier");
		if (count < 1) throw new IllegalArgumentException("count must be >= 1: " + count);
		if (!(chance > 0 && chance <= 1)) throw new IllegalArgumentException("chance must be in (0, 1]: " + chance);
	}

	public boolean fixed() { return chance >= 1.0; }

	/** True when a pickaxe of this tier may get the entry. */
	public boolean allows(ToolTier tier) { return minTier.isEmpty() || tier.ordinal() >= minTier.get().ordinal(); }
}
