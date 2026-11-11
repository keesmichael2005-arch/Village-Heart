package dev.villageheart.core.siege;

/** A random raid that was rolled and has not started (docs/plans/M25.md 3.13). */
public record PendingRaid(int rolledDay, int startTick) {}
