package dev.villageheart.core.smith;

import dev.villageheart.core.Balance;
import dev.villageheart.core.cook.Ingredient;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ToolRecipe;
import dev.villageheart.core.gear.ToolRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Everything the smith makes (M19 plan 3.2 #2). No gold, netherite, chainmail, shovels or spears. */
public final class SmithRecipes {
	public static final String COPPER_TAG = "#minecraft:copper_tool_materials";
	public static final String IRON_TAG = "#minecraft:iron_tool_materials";
	public static final String DIAMOND_TAG = "#minecraft:diamond_tool_materials";
	public static final String PLANKS_TAG = "#minecraft:planks";

	public static final List<SmithRecipe> ALL = build();

	private SmithRecipes() {}

	public static int toolTicks(CraftTier tier) {
		return switch (tier) {
			case WOODEN -> Balance.SMITH_TOOL_WOODEN;
			case STONE -> Balance.SMITH_TOOL_STONE;
			case COPPER -> Balance.SMITH_TOOL_COPPER;
			case IRON -> Balance.SMITH_TOOL_IRON;
			case DIAMOND -> Balance.SMITH_TOOL_DIAMOND;
			case LEATHER -> throw new IllegalArgumentException("leather is not a tool tier");
		};
	}

	public static int armourTicks(CraftTier tier) {
		return switch (tier) {
			case LEATHER -> Balance.SMITH_ARMOUR_LEATHER;
			case COPPER -> Balance.SMITH_ARMOUR_COPPER;
			case IRON -> Balance.SMITH_ARMOUR_IRON;
			case DIAMOND -> Balance.SMITH_ARMOUR_DIAMOND;
			case WOODEN, STONE -> throw new IllegalArgumentException("not an armour tier: " + tier);
		};
	}

	/** The first recipe whose output id is {@code id} (a tool, an armour piece, an ingot, sticks or the golem). */
	public static Optional<SmithRecipe> byOutput(String id) {
		for (SmithRecipe r : ALL) if (r.outputId().equals(id)) return Optional.of(r);
		return Optional.empty();
	}

	public static Optional<SmithRecipe> craft(GearNeed need, CraftTier tier) {
		for (SmithRecipe r : ALL) {
			if (r.kind() == SmithKind.CRAFT && r.need().orElse(null) == need && r.tier().orElse(null) == tier) return Optional.of(r);
		}
		return Optional.empty();
	}

	public static Set<String> rawOres() {
		return Set.of("minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold");
	}

	/**
	 * Lead of the worn line while a smith serves: the craft time of the nearest tier the smith makes, plus the delivery allowance.
	 * Gold shares copper's time; netherite shares diamond's. Zero when no smith serves, so a self-crafter's line is unchanged.
	 */
	public static int replacementLead(dev.villageheart.core.job.ToolTier tier, boolean smithServes) {
		if (!smithServes || tier == null) return 0;
		int ticks = switch (dev.villageheart.core.gear.GearInfo.rankOf(tier)) {
			case 1 -> Balance.SMITH_TOOL_WOODEN;
			case 2 -> Balance.SMITH_TOOL_STONE;
			case 3 -> Balance.SMITH_TOOL_COPPER;
			case 4 -> Balance.SMITH_TOOL_IRON;
			default -> Balance.SMITH_TOOL_DIAMOND;
		};
		return ticks + Balance.SMITH_DELIVERY_LEAD;
	}

	/** A CRAFT row from a tool shape. Wooden and stone pass the objects from {@link ToolRecipes#ALL}. */
	public static SmithRecipe ofTool(ToolRecipe recipe, int ticks) {
		List<Ingredient> inputs = new ArrayList<>();
		inputs.add(new Ingredient(recipe.headTag(), recipe.head()));
		if (recipe.sticks() > 0) inputs.add(new Ingredient(ToolRecipes.STICK, recipe.sticks()));
		return new SmithRecipe(recipe.outputId(), SmithKind.CRAFT, inputs, recipe.outputId(), 1,
				Optional.of(recipe.need()), Optional.of(recipe.tier()), ticks, Optional.of(recipe));
	}

	private static List<SmithRecipe> build() {
		List<SmithRecipe> out = new ArrayList<>();
		for (ToolRecipe r : ToolRecipes.ALL) out.add(ofTool(r, toolTicks(r.tier())));
		for (CraftTier t : List.of(CraftTier.COPPER, CraftTier.IRON, CraftTier.DIAMOND)) {
			for (GearNeed n : GearNeed.tools()) out.add(ofTool(toolShape(n, t), toolTicks(t)));
		}
		for (CraftTier t : List.of(CraftTier.LEATHER, CraftTier.COPPER, CraftTier.IRON, CraftTier.DIAMOND)) {
			for (GearNeed n : GearNeed.armourPieces()) out.add(armour(n, t));
		}
		out.add(smelt("minecraft:iron_ingot", "minecraft:raw_iron"));
		out.add(smelt("minecraft:copper_ingot", "minecraft:raw_copper"));
		out.add(smelt("minecraft:gold_ingot", "minecraft:raw_gold"));
		out.add(new SmithRecipe("minecraft:stick", SmithKind.STICKS, List.of(new Ingredient(PLANKS_TAG, 2)),
				"minecraft:stick", 4, Optional.empty(), Optional.empty(), Balance.SMITH_STICK_TICKS, Optional.empty()));
		out.add(new SmithRecipe("minecraft:iron_golem", SmithKind.GOLEM, List.of(
				new Ingredient("minecraft:iron_ingot", Balance.GOLEM_INGOTS),
				new Ingredient("minecraft:carved_pumpkin", 1)),
				"minecraft:iron_golem", 1, Optional.empty(), Optional.empty(), Balance.GOLEM_BUILD_TICKS, Optional.empty()));
		return List.copyOf(out);
	}

	private static ToolRecipe toolShape(GearNeed n, CraftTier t) {
		int head = switch (n) {
			case AXE, PICKAXE -> 3;
			case HOE, SWORD -> 2;
			default -> throw new IllegalStateException(n.name());
		};
		int sticks = n == GearNeed.SWORD ? 1 : 2;
		String tag = switch (t) {
			case COPPER -> COPPER_TAG;
			case IRON -> IRON_TAG;
			case DIAMOND -> DIAMOND_TAG;
			default -> throw new IllegalStateException(t.name());
		};
		return new ToolRecipe("minecraft:" + t.itemPrefix() + "_" + n.id(), n, t, tag, head, sticks);
	}

	private static SmithRecipe armour(GearNeed n, CraftTier t) {
		int pieces = switch (n) {
			case HEAD -> 5;
			case CHEST -> 8;
			case LEGS -> 7;
			case FEET -> 4;
			default -> throw new IllegalStateException(n.name());
		};
		String mat = switch (t) {
			case LEATHER -> "minecraft:leather";
			case COPPER -> "minecraft:copper_ingot";
			case IRON -> "minecraft:iron_ingot";
			case DIAMOND -> "minecraft:diamond";
			default -> throw new IllegalStateException(t.name());
		};
		String id = "minecraft:" + t.itemPrefix() + "_" + n.piece();
		ToolRecipe shape = new ToolRecipe(id, n, t, mat, pieces, 0);
		return new SmithRecipe(id, SmithKind.CRAFT, List.of(new Ingredient(mat, pieces)), id, 1,
				Optional.of(n), Optional.of(t), armourTicks(t), Optional.of(shape));
	}

	private static SmithRecipe smelt(String output, String raw) {
		return new SmithRecipe(output, SmithKind.SMELT, List.of(new Ingredient(raw, 1)), output, 1,
				Optional.empty(), Optional.empty(), Balance.SMITH_SMELT_TICKS, Optional.empty());
	}
}
