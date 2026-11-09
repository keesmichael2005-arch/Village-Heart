package dev.villageheart.core.gear;

/** A fetch in progress: items reserved and the worker walking to Storage; nothing has moved yet, so no record exists. */
public record PendingFetch(OrderKey key, CraftPlan plan, boolean storedTool, long startedAt) {}
