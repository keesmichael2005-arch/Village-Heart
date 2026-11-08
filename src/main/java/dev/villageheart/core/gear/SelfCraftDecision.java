package dev.villageheart.core.gear;

/** What a worker does about making its own tool this planning step. */
public sealed interface SelfCraftDecision {
	/** Nothing: the job goes on. */
	record None() implements SelfCraftDecision {}

	/** Walk to the job block and work on the saved craft. */
	record Continue() implements SelfCraftDecision {}

	/** The camp cannot be read: the job's own tick runs, the saved craft waits. */
	record Pause() implements SelfCraftDecision {}

	/** Refund the saved craft. */
	record Cancel(CancelReason reason) implements SelfCraftDecision {}

	/** The fetch walk goes on. */
	record KeepFetching() implements SelfCraftDecision {}

	/** Release the reservations; nothing has moved. */
	record AbortFetch() implements SelfCraftDecision {}

	/** Fetch a usable tool from Storage into the camp chest. */
	record FetchStored() implements SelfCraftDecision {}

	/** Reserve the plan's items and walk to Storage. */
	record Start(CraftPlan plan) implements SelfCraftDecision {}

	/** A required tool is missing and cannot be made. */
	record Blocked(BlockReason reason) implements SelfCraftDecision {}

	None NONE = new None();
	Continue CONTINUE = new Continue();
	Pause PAUSE = new Pause();
	KeepFetching KEEP_FETCHING = new KeepFetching();
	AbortFetch ABORT_FETCH = new AbortFetch();
	FetchStored FETCH_STORED = new FetchStored();
}
