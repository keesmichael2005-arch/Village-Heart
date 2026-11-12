package dev.villageheart.core.smith;

import dev.villageheart.core.gear.CancelReason;
import dev.villageheart.core.gear.GearOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.smith.SmithFacts.RepairJob;
import java.util.List;

/** What the smith does next. First match in {@link SmithPlanner} wins. */
public sealed interface SmithDecision {
	/** Nothing this tick. {@code returning} is the walk home after an invalid Smithy. Does not cancel a saved craft or a golem build. */
	record Idle(Hint hint, String material, boolean returning) implements SmithDecision {}

	record Yield() implements SmithDecision {}

	record RepairGolem(int ingots) implements SmithDecision {}

	record Deliver(OrderKey key) implements SmithDecision {}

	record CancelCraft(CancelReason reason) implements SmithDecision {}

	/** Continue the saved craft. {@code consecutiveDefinite} is the cancel counter after this check. */
	record Craft(int consecutiveDefinite) implements SmithDecision {}

	record FetchAndDeliver(OrderKey key, String itemId) implements SmithDecision {}

	record StartCraft(GearOrder order) implements SmithDecision {}

	record BuildGolem() implements SmithDecision {}

	record RepairInPlace(RepairJob job) implements SmithDecision {}

	record FetchForRepair(List<RepairJob> jobs) implements SmithDecision {}

	record StartGolem() implements SmithDecision {}

	record Smelt(SmithBatch.Step batch) implements SmithDecision {}

	enum Hint { OK, TOO_HUNGRY, NEEDS_FUEL, NEEDS_MATERIAL, NEEDS_VILLAGERS, NO_WORK }
}
