package dev.villageheart.core.gear;

/** A vanilla tool recipe: {@code head} of the head material (an item tag, with '#') plus {@code sticks}. */
public record ToolRecipe(String outputId, GearNeed need, CraftTier tier, String headTag, int head, int sticks) {}
