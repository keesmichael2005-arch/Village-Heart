package dev.villageheart.core.job;

/**
 * The outcome of one XP gain.
 *
 * @param reachedCap the XP crossed the next level's threshold during this gain but the tier held the level (the "capped" notice,
 *                   once per threshold because XP only grows)
 */
public record XpChange(Progress before, Progress after, boolean levelledUp, boolean reachedCap) {}
