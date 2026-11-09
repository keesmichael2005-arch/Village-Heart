package dev.villageheart.needs;

/**
 * Test switches (M11 plan 3.2 #12). Production values: needs are real and dinner runs. The GameTest reset forces default needs and turns dinner
 * off, so the worker tests of earlier milestones keep their pace; only the {@code needs_} tests switch them back.
 */
public final class NeedsOverride {
	/** True: {@link NeedsSystem} forces {@code fed} and {@code housed} (no hunger, always housed). {@code paid} is never forced: it always comes from the payroll (M12). */
	public static volatile boolean forceDefaultNeeds;
	/** False: the wage system does nothing but consume the day (M12: no older GameTest ever strikes or loses treasury). Hires are not charged either. */
	public static volatile boolean wagesEnabled = true;
	/** False: the dinner system does nothing. */
	public static volatile boolean dinnerEnabled = true;
	/** False: the dawn does not license a baby (M23); the breeding gate stays on. The GameTest reset turns it off, the growth_ tests turn it on. */
	public static volatile boolean growthEnabled = true;

	private NeedsOverride() {}

	public static void production() {
		forceDefaultNeeds = false;
		dinnerEnabled = true;
		wagesEnabled = true;
		growthEnabled = true;
	}
}
