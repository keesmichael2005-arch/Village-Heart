package dev.villageheart.core.gear;

/** Whether the slot of an open order still exists. HELD and UNKNOWN keep an order; the others are definite reasons to cancel it. */
public enum SlotPresence { HELD, UNKNOWN, COVERED, HIRED_DROPPED, BUILDING_UNREGISTERED }
