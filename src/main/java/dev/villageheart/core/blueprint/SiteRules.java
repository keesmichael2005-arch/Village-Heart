package dev.villageheart.core.blueprint;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a blueprint may be built at a placement (docs/plans/M20.md 3.5). Pure: the world comes in through {@link SiteView}. The rules run in
 * a fixed order and the first failing one is reported, so a test can reach each rule with everything before it passing.
 */
public final class SiteRules {
	private SiteRules() {}

	/** What the glue found in one cell. */
	public enum SiteCell { AIR, CLEARABLE, LOG, FLUID, BLOCKED, UNLOADED }

	public interface SiteView {
		SiteCell cell(Pos p);

		/** The block id of a BLOCKED cell ({@code minecraft:oak_planks}); only asked for BLOCKED cells. */
		String blockId(Pos p);

		/**
		 * A block or hanging thing outside {@code touched} that is not natural and would lose its support when the cell is cleared (a torch on
		 * it, a ladder on its face, a door standing on it, an item frame on it). Only asked for CLEARABLE cells.
		 */
		default Optional<Dependent> dependent(Pos cleared, Set<Long> touched) { return Optional.empty(); }

		/** M21 rule 11c: whether the block at {@code p} can carry a defence (not air, not a fluid; the glue also refuses short grass and snow). */
		default boolean solid(Pos p) {
			SiteCell c = cell(p);
			return c != SiteCell.AIR && c != SiteCell.FLUID;
		}
	}

	/** What depends on a cell: its block id ({@code minecraft:torch}) or entity type id, and where it is. */
	public record Dependent(String id, Pos at) {}

	/** A registered building's area that no site may touch: its marker, the marker's support and the room (interior and shell). */
	public record Protected(String typeName, Pos marker, Set<Long> cells) {
		public Protected {
			cells = Set.copyOf(cells);
		}
	}

	/** The area of a pending build. {@code defence}: its blueprint is a DEFENCE one (M21 rule 8 lets two of those touch). */
	public record Box(Pos min, Pos max, boolean defence) {
		public Box(Pos min, Pos max) { this(min, max, false); }
	}

	public record SiteFacts(Blueprint bp, Placement p, HeartLocation heart, String dimension, Tier tier, Rank rank, int minY, int maxY,
			SiteView view, List<Protected> protectedAreas, List<Box> otherBuilds, int activeBuilds, StandPoints.Standable stand) {}

	public enum Code { RANK, TIER, DIMENSION, HEIGHT, OUTSIDE, TOO_MANY, NOT_LOADED, OVERLAP, BUILD_OVERLAP, FLUID, TREE, BLOCKED, SUPPORT, NO_STAND, UNCHECKED,
		/** M21, appended so the ordinals above stay: rule 11c, and the line problems of {@link WallLines}. */
		FOOTING, NOT_STRAIGHT, TOO_LONG, TOO_SHORT }

	private static final java.util.EnumSet<Code> OVERRIDABLE_BY_UNCHECKED = java.util.EnumSet.of(Code.OVERLAP, Code.BUILD_OVERLAP, Code.FLUID,
			Code.TREE, Code.BLOCKED, Code.SUPPORT, Code.NO_STAND, Code.FOOTING);

	/**
	 * Problems that a room near the site without a scan yet replaces by "try again": every rule from 7 on (the room might be what is in the way).
	 * An explicit set, so it does not rest on the enum order.
	 */
	public static boolean overridableByUnchecked(Code c) { return OVERRIDABLE_BY_UNCHECKED.contains(c); }

	/** The first rule that failed. The args are not cut (a TextLine cuts them for the wire); {@link #english()} is the chat text. */
	public record SiteProblem(Code code, String fallback, List<String> args, @Nullable Pos at) {
		public static final String KEY = "build.villageheart.site.";

		public SiteProblem {
			args = List.copyOf(args);
		}

		public String key() { return KEY + code.name().toLowerCase(java.util.Locale.ROOT); }

		public TextLine text() { return new TextLine(key(), fallback, args); }

		public String english() {
			StringBuilder out = new StringBuilder();
			int next = 0;
			for (int i = 0; i < fallback.length(); i++) {
				if (fallback.startsWith("%s", i)) {
					out.append(next < args.size() ? args.get(next++) : "");
					i++;
				} else {
					out.append(fallback.charAt(i));
				}
			}
			return out.toString();
		}
	}

	private static SiteProblem problem(Code c, String fallback, @Nullable Pos at, String... args) {
		return new SiteProblem(c, fallback, List.of(args), at);
	}

	/** The world cells the blueprint touches at this placement, in blueprint order (layer, z, x). */
	public static List<Pos> touched(Blueprint bp, Placement p) {
		List<Pos> out = new ArrayList<>();
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			if (bp.entry(x, y, z).touched()) out.add(p.world(bp, x, y, z));
		}
		return out;
	}

	/** The area of a box: corners as {@link Box}. */
	public static Box box(Blueprint bp, Placement p) { return new Box(p.min(bp), p.max(bp), bp.kind() == Blueprint.Kind.DEFENCE); }

	public static Optional<SiteProblem> check(SiteFacts f) {
		Blueprint bp = f.bp();
		Placement p = f.p();
		Pos min = p.min(bp), max = p.max(bp);
		// 1 rank
		if (!Permissions.allows(f.rank(), Permission.START_BLUEPRINT)) {
			return Optional.of(problem(Code.RANK, "Only a Steward or the Owner can start a blueprint", null));
		}
		// 2 tier
		if (f.tier().compareTo(bp.tier()) < 0) return Optional.of(problem(Code.TIER, "Requires " + bp.tier().displayName() + " tier", null));
		// 3 dimension and world height
		if (!f.heart().dimension().equals(f.dimension())) return Optional.of(problem(Code.DIMENSION, "Not in this village's dimension", null));
		if (min.y() < f.minY() || max.y() > f.maxY()) return Optional.of(problem(Code.HEIGHT, "Too high or too low", null));
		// 4 the claim sphere is convex: all eight corners inside means the whole box is
		int worst = 0;
		boolean outside = false;
		long r = Balance.CLAIM_RADIUS;
		for (int cx : new int[] {min.x(), max.x()}) for (int cy : new int[] {min.y(), max.y()}) for (int cz : new int[] {min.z(), max.z()}) {
			long dx = cx - f.heart().x(), dy = cy - f.heart().y(), dz = cz - f.heart().z();
			long d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r * r) outside = true;
			worst = Math.max(worst, (int) Math.ceil(Math.sqrt((double) d2)));
		}
		if (outside) {
			return Optional.of(problem(Code.OUTSIDE, "Outside the village: %s blocks from the Heart (max " + Balance.CLAIM_RADIUS + ")", null,
					Integer.toString(worst)));
		}
		// 5. Any number of sites may be placed. The builder finishes them as the blocks arrive. activeBuilds is kept on the facts
		// for callers that still pass it; it does not refuse a site. TOO_MANY stays in the enum so old text keys still resolve.
		// 6 loaded
		for (int y = min.y(); y <= max.y(); y++) for (int z = min.z(); z <= max.z(); z++) for (int x = min.x(); x <= max.x(); x++) {
			if (f.view().cell(new Pos(x, y, z)) == SiteCell.UNLOADED) return Optional.of(problem(Code.NOT_LOADED, "Part of the site is not loaded", new Pos(x, y, z)));
		}
		List<Pos> cells = touched(bp, p);
		// 7 registered buildings
		for (Pos c : cells) {
			for (Protected area : f.protectedAreas()) {
				if (area.cells().contains(c.pack()) || adjacent(area.marker(), c)) {
					return Optional.of(problem(Code.OVERLAP, "Overlaps the %s at %s", c, area.typeName(), area.marker().toString()));
				}
			}
		}
		// 8 other builds, each grown by one so the stand rings never touch
		boolean defence = bp.kind() == Blueprint.Kind.DEFENCE;
		for (Box o : f.otherBuilds()) {
			if (defence && o.defence()) {
				// M21: two defences may touch (walls end to end, a lamp beside a wall), never overlap
				boolean overlap = min.x() <= o.max().x() && max.x() >= o.min().x() && min.z() <= o.max().z() && max.z() >= o.min().z()
						&& min.y() <= o.max().y() && max.y() >= o.min().y();
				if (overlap) return Optional.of(problem(Code.BUILD_OVERLAP, "Overlaps the build at %s", null, o.min().toString()));
				continue;
			}
			boolean hit = min.x() - 1 <= o.max().x() + 1 && max.x() + 1 >= o.min().x() - 1 && min.z() - 1 <= o.max().z() + 1
					&& max.z() + 1 >= o.min().z() - 1 && min.y() - 1 <= o.max().y() + 1 && max.y() + 1 >= o.min().y() - 1;
			if (hit) return Optional.of(problem(Code.BUILD_OVERLAP, "Overlaps the build at %s", null, o.min().toString()));
		}
		// 9 fluids in or next to the site
		for (Pos c : cells) {
			if (f.view().cell(c) == SiteCell.FLUID) return Optional.of(problem(Code.FLUID, "Water or lava at %s", c, c.toString()));
			for (dev.villageheart.core.room.Dir d : dev.villageheart.core.room.Dir.values()) {
				Pos n = c.offset(d);
				if (f.view().cell(n) == SiteCell.FLUID) return Optional.of(problem(Code.FLUID, "Water or lava at %s", n, n.toString()));
			}
		}
		// 10 logs, 11 anything else that is not natural
		for (Pos c : cells) {
			if (f.view().cell(c) == SiteCell.LOG) return Optional.of(problem(Code.TREE, "Tree in the way at %s: fell it first", c, c.toString()));
		}
		for (Pos c : cells) {
			if (f.view().cell(c) == SiteCell.BLOCKED) {
				String id = f.view().blockId(c);
				return Optional.of(problem(Code.BLOCKED, "%s in the way at %s", c, id.substring(id.indexOf(':') + 1), c.toString()));
			}
		}
		// 11b a block of a player that stands on or hangs from a cell the build will clear: it would pop when the cell goes
		Set<Long> touchedSet = new java.util.HashSet<>();
		for (Pos c : cells) touchedSet.add(c.pack());
		for (Pos c : cells) {
			if (f.view().cell(c) != SiteCell.CLEARABLE) continue;
			Optional<Dependent> d = f.view().dependent(c, touchedSet);
			if (d.isPresent()) {
				String id = d.get().id();
				return Optional.of(problem(Code.SUPPORT, "%s at %s would fall: it rests on the site", d.get().at(), id.substring(id.indexOf(':') + 1),
						d.get().at().toString()));
			}
		}
		// 11c M21: a defence stands on solid ground, or a gap under a wall lets mobs through and a lamp or tower hangs in the air
		if (defence) {
			for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
				if (!bp.entry(x, 0, z).isBlock()) continue;
				Pos c = p.world(bp, x, 0, z);
				if (!f.view().solid(c.below())) {
					return Optional.of(problem(Code.FOOTING, "No ground under %s: defences need level ground", c, c.toString()));
				}
			}
		}
		// 12 a place to stand within reach of every touched cell
		List<Pos> ring = StandPoints.ring(min, max, f.stand());
		for (Pos c : cells) {
			boolean ok = false;
			for (Pos s : ring) {
				if (StandPoints.distance(s, c) <= Balance.BUILD_REACH) {
					ok = true;
					break;
				}
			}
			if (!ok) return Optional.of(problem(Code.NO_STAND, "No place to stand near %s", c, c.toString()));
		}
		return Optional.empty();
	}

	private static boolean adjacent(Pos marker, Pos c) {
		int d = Math.abs(marker.x() - c.x()) + Math.abs(marker.y() - c.y()) + Math.abs(marker.z() - c.z());
		return d == 1;
	}

	/** The problem for a registered building whose room has not been scanned yet (after a restart). */
	public static SiteProblem unchecked(String typeName, Pos marker) {
		return problem(Code.UNCHECKED, "The %s at %s has not been checked yet; try again", null, typeName, marker.toString());
	}
}
