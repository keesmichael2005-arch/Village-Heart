package dev.villageheart.core.gear;

import java.util.Optional;

/** The result of one cancel check: the new count of definite checks in a row, and a reason once it reaches two. */
public record CancelCheck(int consecutiveDefinite, Optional<CancelReason> reason) {}
