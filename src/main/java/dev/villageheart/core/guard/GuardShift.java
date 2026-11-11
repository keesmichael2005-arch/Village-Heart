package dev.villageheart.core.guard;

/** A guard's shift and whether a Steward pinned it there (a pinned guard is never moved by the balancer). */
public record GuardShift(Shift shift, boolean pinned) {}
