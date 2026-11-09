package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Which mob a guard fights (M18 3.3). Never a creeper (guards are melee) and never a flying one; a neutral mob (enderman, zombified piglin)
 * only when it targets a villager or hurt this guard. DUTY: in the claim (or it hurt me). OFF / self-defence: only what hurt me, or a
 * hostile or raider within {@link Balance#GUARD_SELF_DEFENCE_RANGE} that targets a villager. Must be visible unless it hurt me.
 * Priority: hurt me, targets a villager, nearest, lower id.
 */
public final class GuardTargets {
	private GuardTargets() {}

	public enum Kind { HOSTILE, RAIDER, CREEPER, NEUTRAL, UNREACHABLE }

	public enum Mode { DUTY, OFF }

	public record Candidate(int id, Kind kind, double distSq, boolean inClaim, boolean visible, boolean targetsVillager, boolean hurtMe) {}

	public static Optional<Candidate> choose(List<Candidate> candidates, Mode mode) {
		double range2 = (double) Balance.GUARD_SELF_DEFENCE_RANGE * Balance.GUARD_SELF_DEFENCE_RANGE;
		return candidates.stream().filter(c -> eligible(c, mode, range2)).min(
				Comparator.comparing((Candidate c) -> !c.hurtMe()).thenComparing(c -> !c.targetsVillager())
						.thenComparingDouble(Candidate::distSq).thenComparingInt(Candidate::id));
	}

	private static boolean eligible(Candidate c, Mode mode, double range2) {
		if (c.kind() == Kind.CREEPER || c.kind() == Kind.UNREACHABLE) return false;
		if (!c.visible() && !c.hurtMe()) return false;
		if (c.kind() == Kind.NEUTRAL && !c.targetsVillager() && !c.hurtMe()) return false;
		if (mode == Mode.DUTY) return c.inClaim() || c.hurtMe();
		if (c.hurtMe()) return true;
		return (c.kind() == Kind.HOSTILE || c.kind() == Kind.RAIDER) && c.distSq() <= range2 && c.targetsVillager();
	}
}
