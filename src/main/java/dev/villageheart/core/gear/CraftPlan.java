package dev.villageheart.core.gear;

import java.util.List;

/**
 * What a self-craft takes and leaves: {@code take} names the concrete stockpile ids to reserve and withdraw, {@code stickBatches} the
 * 2-planks-to-4-sticks steps, {@code logs} the logs turned into planks, {@code leftovers} what goes back to Storage after the craft.
 */
public record CraftPlan(ToolRecipe recipe, List<ItemCount> take, int stickBatches, int logs, List<ItemCount> leftovers) {
	public CraftPlan {
		take = List.copyOf(take);
		leftovers = List.copyOf(leftovers);
	}
}
