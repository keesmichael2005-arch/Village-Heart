package dev.villageheart.core.guide;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.building.ShapedSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The crafting grid shown beside a guide task. Empty slots are "". */
public final class GuideRecipes {
	private GuideRecipes() {}

	public static List<String> grid(GuideTask task) {
		String item = task.itemId();
		if (item == null) return List.of();
		if (item.equals("villageheart:blank_marker")) return cells(blank());
		if (item.equals("villageheart:blueprint_book")) {
			return List.of("minecraft:book", "minecraft:paper", "minecraft:paper", "", "", "", "", "", "");
		}
		for (JobBlockType block : JobBlockType.values()) {
			if (item.equals("villageheart:" + block.id())) return cells(block.recipe());
		}
		String path = item.substring("villageheart:".length());
		if (path.endsWith("_marker")) {
			var building = BuildingType.byId(path.substring(0, path.length() - "_marker".length()));
			if (building.isPresent()) return building.get().ring().grid();
		}
		return List.of();
	}

	private static ShapedSpec blank() {
		return new ShapedSpec(List.of("PPP", "PFP", "PPP"),
				Map.of('P', "#minecraft:planks", 'F', "minecraft:item_frame"), "villageheart:blank_marker", 1);
	}

	private static List<String> cells(ShapedSpec spec) {
		List<String> out = new ArrayList<>();
		for (String slot : spec.grid()) out.add(slot == null ? "" : slot);
		return out;
	}
}
