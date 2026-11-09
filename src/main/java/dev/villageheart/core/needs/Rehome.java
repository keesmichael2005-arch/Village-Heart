package dev.villageheart.core.needs;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;

/** Which House bed an unhoused worker should take (M11 plan 3.2 #9). The glue does the POI work. */
public final class Rehome {
	private Rehome() {}

	public enum Holder { NONE, WORKER, RESIDENT_AWAKE, RESIDENT_ASLEEP }

	public record BedState(Pos pos, Holder holder) {}

	/** @param evict true when an awake resident has to give the bed up */
	public record Choice(Pos bed, boolean evict) {}

	/** The nearest free bed; else the nearest bed held by an awake non-worker. A worker's or a sleeper's bed is never taken. */
	public static Optional<Choice> choose(Pos worker, List<BedState> houseBeds) {
		Optional<Choice> free = nearest(worker, houseBeds, Holder.NONE).map(p -> new Choice(p, false));
		if (free.isPresent()) return free;
		return nearest(worker, houseBeds, Holder.RESIDENT_AWAKE).map(p -> new Choice(p, true));
	}

	private static Optional<Pos> nearest(Pos from, List<BedState> beds, Holder holder) {
		Pos best = null;
		double bestD = Double.MAX_VALUE;
		for (BedState b : beds) {
			if (b.holder() != holder) continue;
			double dx = b.pos().x() - from.x(), dy = b.pos().y() - from.y(), dz = b.pos().z() - from.z();
			double d = dx * dx + dy * dy + dz * dz;
			if (best == null || d < bestD || (d == bestD && b.pos().compareTo(best) < 0)) {
				best = b.pos();
				bestD = d;
			}
		}
		return Optional.ofNullable(best);
	}
}
