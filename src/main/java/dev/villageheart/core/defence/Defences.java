package dev.villageheart.core.defence;

import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** World positions of the recorded defences: key blocks and patrol points (docs/plans/M21.md 3.2 #8). */
public final class Defences {
	private Defences() {}

	/** A key block's world cell and the block id it must hold. */
	public record Key(Pos at, String blockId) {}

	private static Placement placement(DefenceSite s) {
		return new Placement(new Pos(s.ground().x(), s.ground().y(), s.ground().z()), s.rotation());
	}

	/** The cells whose blocks make the site; empty for an unknown blueprint. */
	public static List<Key> keys(DefenceSite s) {
		Optional<DefenceSpecs.Spec> spec = DefenceSpecs.of(s.blueprintId());
		Optional<Blueprint> bp = Blueprints.byId(s.blueprintId());
		if (spec.isEmpty() || bp.isEmpty()) return List.of();
		Placement p = placement(s);
		List<Key> out = new ArrayList<>();
		for (DefenceSpecs.KeyCell k : spec.get().keys()) out.add(new Key(p.world(bp.get(), k.x(), k.y(), k.z()), k.blockId()));
		return out;
	}

	/** The candidate patrol cells in order (feet level), in the world. */
	public static List<Pos> candidates(DefenceSite s) {
		Optional<DefenceSpecs.Spec> spec = DefenceSpecs.of(s.blueprintId());
		Optional<Blueprint> bp = Blueprints.byId(s.blueprintId());
		if (spec.isEmpty() || bp.isEmpty()) return List.of();
		Placement p = placement(s);
		List<Pos> out = new ArrayList<>();
		for (int[] c : spec.get().patrolCandidates()) out.add(p.world(bp.get(), c[0], 0, c[1]));
		return out;
	}

	/** The first candidate cell that {@code walkable} accepts (a person can stand there), if any. */
	public static Optional<Pos> patrolPoint(DefenceSite s, Predicate<Pos> walkable) {
		for (Pos c : candidates(s)) if (walkable.test(c)) return Optional.of(c);
		return Optional.empty();
	}

	/** One patrol point per site that has one, in record order (= the order the sites were finished). */
	public static List<Pos> patrolPoints(List<DefenceSite> sites, Predicate<Pos> walkable) {
		List<Pos> out = new ArrayList<>();
		for (DefenceSite s : sites) patrolPoint(s, walkable).ifPresent(out::add);
		return out;
	}

	/** True for the blueprints that are recorded when finished. */
	public static boolean recorded(String blueprintId) { return DefenceSpecs.of(blueprintId).isPresent(); }
}
