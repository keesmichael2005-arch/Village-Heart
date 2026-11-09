package dev.villageheart.core.economy;

import java.util.Objects;
import java.util.UUID;

/** A worker and the wage it costs (already looked up from its level). */
public record Payee(UUID villager, int wage) {
	public Payee {
		Objects.requireNonNull(villager, "villager");
		if (wage < 0) throw new IllegalArgumentException("wage must be >= 0: " + wage);
	}
}
