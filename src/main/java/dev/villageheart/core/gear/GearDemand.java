package dev.villageheart.core.gear;

import dev.villageheart.core.HeartLocation;
import java.util.OptionalInt;

/** One slot the camp needs filled. {@code destination} is the camp for tools. */
public record GearDemand(OrderKey key, String job, Urgency urgency, SlotState state, HeartLocation destination,
		OptionalInt currentRank, boolean paceDependsOnTier) {}
