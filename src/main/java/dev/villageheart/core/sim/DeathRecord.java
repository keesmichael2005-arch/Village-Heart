package dev.villageheart.core.sim;

/** One villager lost: the cause id (e.g. minecraft:zombie, minecraft:fall, zombified), whether a hostile mob did it, and when. */
public record DeathRecord(String cause, boolean byMob, long gameTime) {}
