package dev.villageheart.core.economy;

import java.util.List;
import java.util.UUID;

/** The outcome of one wage run. {@code treasuryBefore} is the value before the run (M14 and M23 read it from the dawn listeners). */
public record PayResult(long day, int treasuryBefore, int treasuryAfter, int spent, int bill, List<UUID> paid, List<Payee> unpaid) {
	public PayResult {
		paid = List.copyOf(paid);
		unpaid = List.copyOf(unpaid);
	}

	/** A dawn on which nobody was paid or charged (wages off): the treasury is unchanged. */
	public static PayResult none(long day, int treasury) { return new PayResult(day, treasury, treasury, 0, 0, List.of(), List.of()); }

	public List<UUID> unpaidIds() { return unpaid.stream().map(Payee::villager).toList(); }
}
