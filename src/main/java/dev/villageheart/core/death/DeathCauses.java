package dev.villageheart.core.death;

import java.util.Optional;

/**
 * Why a villager was lost (M25). The death log and the simulation collector both call this, so a witch and a
 * zombie conversion cannot be counted two different ways.
 */
public final class DeathCauses {
	private DeathCauses() {}

	/** A cause id and whether a hostile mob did it ({@code deaths.mobs} counts {@code byMob}, plus {@code zombified}). */
	public record Cause(String cause, boolean byMob) {}

	/**
	 * A death. A source entity wins over the damage type (a zombie's hit is {@code minecraft:zombie}, not
	 * {@code minecraft:mob_attack}). {@code byMob} follows {@code sourceIsEnemy} only when there is a source:
	 * a player is a source and is not a mob.
	 */
	public static Cause of(Optional<String> sourceEntityTypeId, boolean sourceIsEnemy, String damageTypeId) {
		if (sourceEntityTypeId != null && sourceEntityTypeId.isPresent() && !sourceEntityTypeId.get().isEmpty()) {
			return new Cause(sourceEntityTypeId.get(), sourceIsEnemy);
		}
		String damage = damageTypeId == null || damageTypeId.isEmpty() ? "unknown" : damageTypeId;
		return new Cause(damage, false);
	}

	/**
	 * A conversion worth one log line: a villager becoming a zombie villager ({@code zombified}) or a witch
	 * ({@code minecraft:lightning_bolt}). A cure, and every other conversion, is empty.
	 */
	public static Optional<String> conversion(String fromType, String toType) {
		if (!"minecraft:villager".equals(fromType)) return Optional.empty();
		if ("minecraft:zombie_villager".equals(toType)) return Optional.of("zombified");
		if ("minecraft:witch".equals(toType)) return Optional.of("minecraft:lightning_bolt");
		return Optional.empty();
	}

	/** {@code zombified} counts as a mob death. Lightning does not. */
	public static boolean conversionCountsAsMob(String cause) {
		return "zombified".equals(cause);
	}

	/**
	 * Death and conversion of the same villager are one loss when the conversion is not more than one tick
	 * after the death. A conversion that arrives earlier still merges (the M2 collector's rule).
	 */
	public static boolean sameLoss(long deathTime, long conversionTime) {
		return conversionTime - deathTime <= 1;
	}
}
