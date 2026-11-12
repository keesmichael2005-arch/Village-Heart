package dev.villageheart.core.gear;

import dev.villageheart.core.smith.SmithRecipe;
import java.util.List;

/**
 * One thing the smith will make for one demand slot. {@code plan} names the concrete stockpile stacks to reserve;
 * {@code prerequisites} run first (smelt, then sticks), one entry per unit.
 */
public record GearOrder(GearDemand demand, SmithRecipe recipe, CraftPlan plan, List<SmithRecipe> prerequisites) {
	public GearOrder {
		prerequisites = List.copyOf(prerequisites);
	}

	public OrderKey key() { return demand.key(); }
}
