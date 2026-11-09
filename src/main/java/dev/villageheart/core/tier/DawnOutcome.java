package dev.villageheart.core.tier;

import dev.villageheart.core.Tier;

public record DawnOutcome(Tier tier, TierProgress progress, TierReport report, boolean tieredUp) {}
