package dev.villageheart.core.smith;

import dev.villageheart.core.cook.Ingredient;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ToolRecipe;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing the smith makes. An input id may be an item ({@code minecraft:iron_ingot}) or a tag ({@code #minecraft:planks}).
 * CRAFT rows carry the {@link ToolRecipe} shape: wooden and stone tools are the same objects as {@code ToolRecipes.ALL}.
 */
public record SmithRecipe(String id, SmithKind kind, List<Ingredient> inputs, String outputId, int outputCount,
		Optional<GearNeed> need, Optional<CraftTier> tier, int ticksPerUnit, Optional<ToolRecipe> shape) {
	public SmithRecipe {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(outputId, "outputId");
		Objects.requireNonNull(need, "need");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(shape, "shape");
		inputs = List.copyOf(inputs);
		if (id.isBlank() || outputId.isBlank()) throw new IllegalArgumentException("blank id");
		if (outputCount < 1) throw new IllegalArgumentException("outputCount < 1");
		if (ticksPerUnit < 1) throw new IllegalArgumentException("ticksPerUnit < 1");
		if (kind == SmithKind.CRAFT && (need.isEmpty() || tier.isEmpty() || shape.isEmpty()))
			throw new IllegalArgumentException("a craft needs a need, a tier and a shape");
	}
}
