package dev.villageheart.core.sim;

/**
 * One villager lost: the cause id (e.g. minecraft:zombie, minecraft:fall, zombified), whether a hostile mob did it, and when. The rest is the
 * death log (M26 prep), filled in by the simulation and null/empty in older reports: the day time (0..23999), the villager's name, job ("" when
 * unemployed), the damage type id, the attacker's entity type ("" when none), the block position, and where that is against the scenario's
 * wall ring ({@link WallRing#INSIDE}, {@link WallRing#ON_WALL}, {@link WallRing#OUTSIDE}, or "" when the scenario has none), then the
 * attacker's origin and its side of the ring.
 */
public record DeathRecord(String cause, boolean byMob, long gameTime, Detail detail) {
	public record Detail(long dayTime, String name, String job, String damage, String attacker, int x, int y, int z, String wall,
			String origin, String attackerWall) {}

	/** Where the attacker came from ({@link Detail#origin}): a scenario {@code night_spawn}, a converted villager, any other (natural) mob, or "" (no attacker). */
	public static final String SCRIPTED = "scripted", CONVERTED = "converted", NATURAL = "natural";

	public DeathRecord(String cause, boolean byMob, long gameTime) { this(cause, byMob, gameTime, null); }

	public static final String ZOMBIFIED = "zombified";

	public boolean zombified() { return ZOMBIFIED.equals(cause); }

	public boolean guard() { return detail != null && "guard".equals(detail.job()); }

	public DeathRecord withCause(String newCause, boolean newByMob) { return new DeathRecord(newCause, newByMob, gameTime, detail); }
}
