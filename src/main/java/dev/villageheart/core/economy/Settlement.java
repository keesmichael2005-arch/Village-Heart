package dev.villageheart.core.economy;

import java.util.List;
import java.util.UUID;

/** A payroll after a settle or a hire, what is left in the treasury and who was paid by it. */
public record Settlement(Payroll payroll, int treasuryAfter, List<UUID> paid) {
	public Settlement {
		paid = List.copyOf(paid);
	}
}
