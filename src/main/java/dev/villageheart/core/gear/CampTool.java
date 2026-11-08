package dev.villageheart.core.gear;

import dev.villageheart.core.job.ToolTier;

/** A tool in a camp container. {@code order} = container x 1000 + slot (ties); {@code rank}: higher is given out first (tier, or the hunter's bows). */
public record CampTool(int order, ToolTier tier, int rank, int remaining, int max) {}
