package dev.villageheart.core.sim;

/** Outcome of one assertion: the source text, whether it held, and a human detail naming the first failure. */
public record AssertionResult(String text, boolean passed, String detail) {}
