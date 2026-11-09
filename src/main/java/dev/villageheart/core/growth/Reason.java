package dev.villageheart.core.growth;

/** The outcome of a dawn decision; the first failing check, in this order of checking: NOBODY, NO_FREE_BED, LOW_FOOD, TOO_FEW_READY. */
public enum Reason { LICENSED, NO_FREE_BED, LOW_FOOD, TOO_FEW_READY, NOBODY }
