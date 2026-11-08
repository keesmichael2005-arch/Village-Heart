package dev.villageheart.core.blueprint;

import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ordered steps that build a blueprint at a placement (docs/plans/M20.md 3.2 #7). Deterministic: the same inputs give the same list.
 * <ol>
 * <li>CLEAR every touched cell, top layer first;</li>
 * <li>PLACE layer by layer from the bottom, MAIN pass entries only, each layer in perimeter order (angle around the box centre, then
 *     distance) so the builder walks round once per layer; a two-block unit (door, bed) is one step at its primary cell;</li>
 * <li>PLACE the ATTACHED entries except the marker (torches), then the LAST entries (water);</li>
 * <li>CHECK: one per PLACE step above (not the marker): placed again when the cell no longer holds its block;</li>
 * <li>PLACE the marker, the very last step, so the room is complete when it is first checked.</li>
 * </ol>
 */
public final class BuildPlan {
	private BuildPlan() {}

	public enum Kind { CLEAR, PLACE, CHECK }

	/** The second half of a unit: its cell and its palette entry (state in the blueprint's own frame). */
	public record Part(Pos pos, PaletteEntry entry) {}

	public record Step(int index, Kind kind, Pos pos, PaletteEntry entry, List<Part> parts) {
		public Step {
			parts = List.copyOf(parts);
		}

		/** Material id of a PLACE or CHECK step, null for CLEAR. */
		public String materialId() { return kind == Kind.CLEAR || entry.material() == null ? null : entry.material().id(); }
	}

	private record Cell(Pos pos, PaletteEntry entry, int layer, double angle, double dist, List<Part> parts) {}

	public static List<Step> of(Blueprint bp, Placement p) {
		Pos min = p.min(bp), max = p.max(bp);
		double cx = (min.x() + max.x() + 1) / 2.0, cz = (min.z() + max.z() + 1) / 2.0;
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			PaletteEntry e = bp.entry(x, y, z);
			if (!e.touched()) continue;
			Pos w = p.world(bp, x, y, z);
			double dx = w.x() + 0.5 - cx, dz = w.z() + 0.5 - cz;
			double angle = Math.atan2(dz, dx);
			if (angle < 0) angle += 2 * Math.PI;
			List<Part> parts = new ArrayList<>();
			if (e.primary()) {
				for (Dir d : Dir.values()) {
					int px = x - d.dx, py = y - d.dy, pz = z - d.dz;
					if (px < 0 || px >= bp.width() || py < 0 || py >= bp.height() || pz < 0 || pz >= bp.depth()) continue;
					PaletteEntry pe = bp.entry(px, py, pz);
					if (pe.partOf() == d) parts.add(new Part(p.world(bp, px, py, pz), pe));
				}
			}
			cells.add(new Cell(w, e, y, angle, Math.hypot(dx, dz), parts));
		}
		Comparator<Cell> perimeter = Comparator.comparingInt(Cell::layer).thenComparingDouble(Cell::angle).thenComparingDouble(Cell::dist)
				.thenComparing(c -> c.pos());
		List<Step> steps = new ArrayList<>();
		// (a) clear, top layer first
		List<Cell> clear = new ArrayList<>(cells);
		clear.sort(Comparator.comparingInt((Cell c) -> -c.layer()).thenComparingDouble(Cell::angle).thenComparingDouble(Cell::dist)
				.thenComparing(c -> c.pos()));
		for (Cell c : clear) steps.add(new Step(steps.size(), Kind.CLEAR, c.pos(), c.entry(), List.of()));
		// (b) (c) (d) place by pass
		List<Cell> sorted = new ArrayList<>(cells);
		sorted.sort(perimeter);
		PaletteEntry marker = bp.markerEntry();
		List<Cell> placed = new ArrayList<>();
		for (PaletteEntry.Pass pass : PaletteEntry.Pass.values()) {
			for (Cell c : sorted) {
				if (!c.entry().primary() || c.entry().pass() != pass || c.entry() == marker) continue;
				steps.add(new Step(steps.size(), Kind.PLACE, c.pos(), c.entry(), c.parts()));
				placed.add(c);
			}
		}
		// (e) check pass
		for (Cell c : placed) steps.add(new Step(steps.size(), Kind.CHECK, c.pos(), c.entry(), c.parts()));
		// (f) the marker
		if (marker != null) {
			for (Cell c : sorted) {
				if (c.entry() == marker) steps.add(new Step(steps.size(), Kind.PLACE, c.pos(), c.entry(), List.of()));
			}
		}
		return List.copyOf(steps);
	}

	/** Number of PLACE steps (the progress denominator). */
	public static int placeCount(List<Step> steps) {
		int n = 0;
		for (Step s : steps) if (s.kind() == Kind.PLACE) n++;
		return n;
	}

	/** PLACE steps before {@code cursor}. */
	public static int placedBefore(List<Step> steps, int cursor) {
		int n = 0;
		for (int i = 0; i < Math.min(cursor, steps.size()); i++) if (steps.get(i).kind() == Kind.PLACE) n++;
		return n;
	}

	/** Progress in whole percent, rounded down: PLACE steps done of all PLACE steps. */
	public static int percent(List<Step> steps, int cursor) {
		int total = placeCount(steps);
		return total == 0 ? 100 : placedBefore(steps, cursor) * 100 / total;
	}
}
