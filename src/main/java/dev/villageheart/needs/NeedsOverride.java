package dev.villageheart.needs;

/**
 * Test switches (M11 plan 3.2 #12). Production values: needs are real and dinner runs. The GameTest reset forces default needs and turns dinner
 * off, so the worker tests of earlier milestones keep their pace; only the {@code needs_} tests switch them back.
 */
public final class NeedsOverride {
	/** True: {@link NeedsSystem} writes {@code Needs.DEFAULT} for every worker (no hunger, always housed, paid). */
	public static volatile boolean forceDefaultNeeds;
	/** False: the dinner system does nothing. */
	public static volatile boolean dinnerEnabled = true;

	private NeedsOverride() {}

	public static void production() {
		forceDefaultNeeds = false;
		dinnerEnabled = true;
	}
}
