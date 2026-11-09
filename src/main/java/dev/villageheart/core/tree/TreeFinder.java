package dev.villageheart.core.tree;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.TreeScan.NotATree;
import dev.villageheart.core.tree.TreeScan.Tree;
import dev.villageheart.core.tree.TreeScan.Why;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds natural trees around a centre, nearest column first, in a resumable and budgeted way: one {@link #step} reads cells until its
 * budget is spent (checked between columns), then reports {@link Paused}; the next step goes on where it stopped, so the sequence of
 * results does not depend on the budget. A tree is a candidate when its first base log is within {@code radius} blocks (xz, inclusive)
 * and {@code dy} blocks (y) of the centre.
 */
public final class TreeFinder {
	public sealed interface Result {}

	public record Found(Tree tree) implements Result {}

	public enum Progress implements Result { PAUSED, EXHAUSTED }

	private static final Map<Integer, int[][]> RINGS = new HashMap<>();

	/** All (dx, dz) with dx^2 + dz^2 <= r^2 sorted by distance, then by angle: the order is the same for every finder of that radius. */
	private static synchronized int[][] ring(int radius) {
		return RINGS.computeIfAbsent(radius, r -> {
			List<int[]> l = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (dx * dx + dz * dz <= r * r) l.add(new int[] {dx, dz});
			l.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
					.thenComparingDouble(o -> Math.atan2(o[1], o[0]))
					.thenComparingInt(o -> o[0]));
			return l.toArray(new int[0][]);
		});
	}

	private final Pos centre;
	private final int radius, dy;
	private final int[][] columns;
	private int next;
	private boolean sawUnknown;
	private final Set<Long> rejected = new HashSet<>();
	private final Set<Long> accepted = new HashSet<>();
	private int lastReads;

	public TreeFinder(Pos centre, int radius, int dy) {
		this.centre = centre;
		this.radius = radius;
		this.dy = dy;
		this.columns = ring(radius);
	}

	/** True when a flood met an unloaded cell: "exhausted" then only means "nothing found yet". */
	public boolean sawUnknown() { return sawUnknown; }

	public int columnsDone() { return next; }

	public int columnsTotal() { return columns.length; }

	/** Cell reads of the last {@link #step} (including the ones of the scans). */
	public int lastReads() { return lastReads; }

	private static final class Counting implements TreeView {
		final TreeView inner;
		int reads;

		Counting(TreeView inner) { this.inner = inner; }

		@Override public TreeCell cell(int x, int y, int z) { reads++; return inner.cell(x, y, z); }
		@Override public int top(int x, int z) { reads++; return inner.top(x, z); }
		@Override public boolean inRoom(Pos p) { return inner.inRoom(p); }
	}

	public Result step(TreeView view, int readBudget, Predicate<Pos> skipBase) {
		Counting v = new Counting(view);
		try {
			if (resumeActive) {
				resumeActive = false;
				Tree t = descend(v, resumeX, resumeZ, resumeY, skipBase);
				if (t != null) return new Found(t);
			}
			while (next < columns.length) {
				if (v.reads >= readBudget) return Progress.PAUSED;
				int[] c = columns[next++];
				Tree t = column(v, centre.x() + c[0], centre.z() + c[1], skipBase);
				if (t != null) return new Found(t);
			}
			return Progress.EXHAUSTED;
		} finally {
			lastReads = v.reads;
		}
	}

	private Tree column(Counting v, int x, int z, Predicate<Pos> skipBase) {
		int top = v.top(x, z);
		if (top == Integer.MIN_VALUE) {
			sawUnknown = true;
			return null;
		}
		return descend(v, x, z, Math.min(top, centre.y() + dy + 60), skipBase);
	}

	/** A tree found part-way down a column: the rest of the column (a small tree below a branch) is searched by the next step. */
	private boolean resumeActive;
	private int resumeX, resumeZ, resumeY;

	private Tree descend(Counting v, int x, int z, int startY, Predicate<Pos> skipBase) {
		int floor = centre.y() - dy - 2;
		int y = startY;
		for (int steps = 0; y >= floor && steps < 64; y--, steps++) {
			TreeCell cell = v.cell(x, y, z);
			switch (cell.kind()) {
				case UNLOADED -> {
					sawUnknown = true;
					return null;
				}
				case SOIL -> {
					return null;
				}
				case LOG -> {
					Pos p = new Pos(x, y, z);
					long key = p.pack();
					if (rejected.contains(key) || accepted.contains(key)) continue;
					TreeScan s = TreeScanner.scan(v, p);
					if (s instanceof NotATree n) {
						if (n.why() == Why.UNKNOWN) sawUnknown = true;
						else for (Pos q : n.logs()) rejected.add(q.pack());
						rejected.add(key);
					} else if (s instanceof Tree t) {
						for (Pos q : t.fellOrder()) accepted.add(q.pack());
						Pos b = t.base().get(0);
						long ddx = b.x() - centre.x(), ddz = b.z() - centre.z();
						boolean inRange = ddx * ddx + ddz * ddz <= (long) radius * radius && Math.abs(b.y() - centre.y()) <= dy;
						if (inRange && !skipBase.test(b)) {
							resumeActive = true;
							resumeX = x;
							resumeZ = z;
							resumeY = y - 1;
							return t;
						}
					}
				}
				default -> { }
			}
		}
		return null;
	}
}
