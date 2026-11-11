package dev.villageheart.core.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The eight wooden and stone tool recipes (vanilla counts). M19's smith reuses them for its wooden and stone rows. */
public final class ToolRecipes {
	public static final int PLANKS_PER_STICK_BATCH = 2, STICKS_PER_BATCH = 4, PLANKS_PER_LOG = 4;
	public static final String PLANKS_TAG = "#minecraft:planks";
	public static final String STONE_TAG = "#minecraft:stone_tool_materials";
	public static final String STICK = "minecraft:stick";

	public static final List<ToolRecipe> ALL = build();

	private ToolRecipes() {}

	private static List<ToolRecipe> build() {
		// Self-made tools only. M19 appends armour needs and higher tiers to the enums; those recipes live in SmithRecipes, not here.
		List<ToolRecipe> out = new ArrayList<>();
		for (CraftTier t : List.of(CraftTier.WOODEN, CraftTier.STONE)) {
			String tag = t == CraftTier.WOODEN ? PLANKS_TAG : STONE_TAG;
			for (GearNeed n : GearNeed.tools()) {
				int head = switch (n) {
					case AXE, PICKAXE -> 3;
					case HOE, SWORD -> 2;
					case HEAD, CHEST, LEGS, FEET -> throw new IllegalStateException("armour is not a self-made tool");
				};
				int sticks = n == GearNeed.SWORD ? 1 : 2;
				out.add(new ToolRecipe("minecraft:" + t.itemPrefix() + "_" + n.id(), n, t, tag, head, sticks));
			}
		}
		return List.copyOf(out);
	}

	public static ToolRecipe of(GearNeed n, CraftTier t) {
		for (ToolRecipe r : ALL) if (r.need() == n && r.tier() == t) return r;
		throw new IllegalArgumentException("no recipe for " + n + " " + t);
	}

	public static Optional<ToolRecipe> byOutput(String id) {
		for (ToolRecipe r : ALL) if (r.outputId().equals(id)) return Optional.of(r);
		return Optional.empty();
	}
}
