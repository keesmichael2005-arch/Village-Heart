package dev.villageheart.core.blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Which plans the Blueprint Book shows, in menu order, split into {@link BlueprintGroup}. Placing a plan is not limited by how many
 * are already down, or by whether Storage has the blocks: the builder finishes them as the materials arrive.
 */
public final class BlueprintCatalog {
	private BlueprintCatalog() {}

	public static BlueprintGroup groupOf(Blueprint bp) {
		return switch (bp.id()) {
			case "farm_plot", "hunting_grove" -> BlueprintGroup.PRODUCTION;
			case "wall_segment", "gate", "lamp_post", "watchtower" -> BlueprintGroup.DEFENCE;
			default -> BlueprintGroup.BUILDINGS;
		};
	}

	/** Plans in {@code group}, in the same order as {@link Blueprints#all()}. */
	public static List<Blueprint> in(BlueprintGroup group) {
		List<Blueprint> out = new ArrayList<>();
		for (Blueprint bp : Blueprints.all()) if (groupOf(bp) == group) out.add(bp);
		return List.copyOf(out);
	}
}
