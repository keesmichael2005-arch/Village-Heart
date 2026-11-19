package dev.villageheart.core.farm;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

/**
 * The crop and empty-farmland cells of one Farmhouse, found by a budgeted sweep and kept fresh by a budgeted round-robin
 * refresh. The sweep reads the seed disc of every composter, then farmland that shares a side with it, including a step of
 * one block. Budgets count block reads: one {@link FieldView#cell} is charged {@link #READS_PER_CELL}. The index is updated
 * cell by cell, so it is usable while a sweep is still running. Not saved.
 */
public final class FieldIndex {
	/** A cell read looks at the crop cell and the block below it. */
	public static final int READS_PER_CELL = 2;

	public record Indexed(Pos pos, FieldCell cell) {}

	private static final Map<Integer, int[][]> COLUMNS = new HashMap<>();
	private static final int[] STEP_X = {1, -1, 0, 0};
	private static final int[] STEP_Z = {0, 0, 1, -1};

	private final List<Pos> composters;
	private final Pos jobBlock;
	private final int[][] columns;
	private final TreeMap<Pos, FieldCell> known = new TreeMap<>();
	private final Set<Long> visited = new HashSet<>();
	private final PriorityQueue<Pos> frontier;
	private int origin;
	private int column;
	private int dy;
	private boolean discDone;
	private boolean sweepDone;
	private boolean sawUnloaded;
	private boolean unloadedThisSweep;
	private boolean capped;
	private Pos[] refreshOrder = new Pos[0];
	private int refreshAt;
	private long sweepCompletedAt = Long.MIN_VALUE;

	public FieldIndex(Pos jobBlock) { this(List.of(jobBlock)); }

	/** One field for every composter of the Farmhouse. The list is copied. */
	public FieldIndex(List<Pos> composters) {
		if (composters.isEmpty()) throw new IllegalArgumentException("a field needs a composter");
		this.composters = List.copyOf(composters);
		this.jobBlock = this.composters.get(0);
		this.columns = columnsFor(Balance.FARM_RADIUS);
		this.dy = Balance.FARM_DY;
		this.frontier = new PriorityQueue<>(Comparator
				.<Pos>comparingLong(p -> FieldReach.nearestSquared(this.composters, p))
				.thenComparing(p -> p));
	}

	public Pos jobBlock() { return jobBlock; }

	public List<Pos> composters() { return composters; }

	/** The (dx, dz) columns of the disc, nearest first, shared by every index of the same radius. */
	public static synchronized int[][] columnsFor(int radius) {
		return COLUMNS.computeIfAbsent(radius, r -> {
			List<int[]> list = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (dx * dx + dz * dz <= r * r) list.add(new int[] {dx, dz});
			list.sort((a, b) -> {
				int c = Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]);
				if (c != 0) return c;
				return Double.compare(Math.atan2(a[1], a[0]), Math.atan2(b[1], b[0]));
			});
			return list.toArray(new int[0][]);
		});
	}

	public boolean sweepComplete() { return sweepDone; }

	/** True when the last complete sweep met a column in an unloaded chunk: "no farmland" is then not certain. */
	public boolean sawUnloaded() { return sawUnloaded; }

	/** True when the cell cap cut the index short. */
	public boolean capped() { return capped; }

	public long sweepCompletedAt() { return sweepCompletedAt; }

	/** Starts the next sweep from the nearest column; what is known stays until it is read again. */
	public void restartSweep() {
		origin = 0;
		column = 0;
		dy = Balance.FARM_DY;
		discDone = false;
		sweepDone = false;
		unloadedThisSweep = false;
		visited.clear();
		frontier.clear();
	}

	/**
	 * Reads cells of the sweep for at most {@code readBudget} block reads (one cell is read at least while the sweep is not done and the
	 * budget is positive). Returns the reads spent. A column in an unloaded chunk is skipped as a whole and keeps what was known.
	 */
	public int sweep(FieldView v, int readBudget, long now) {
		if (sweepDone) return 0;
		int spent = 0;
		while (!discDone && spent < readBudget) {
			Pos centre = composters.get(origin);
			int[] c = columns[column];
			int x = centre.x() + c[0], z = centre.z() + c[1], y = centre.y() + dy;
			if (visited.add(Pos.pack(x, y, z))) {
				FieldCell cell = v.cell(x, y, z);
				spent += READS_PER_CELL;
				if (cell.kind() == FieldCell.Kind.UNLOADED) {
					unloadedThisSweep = true;
					dy = -Balance.FARM_DY - 1; // the rest of an unloaded column is skipped
				} else {
					store(new Pos(x, y, z), cell);
				}
			}
			if (--dy < -Balance.FARM_DY) {
				dy = Balance.FARM_DY;
				if (++column >= columns.length) {
					column = 0;
					if (++origin >= composters.size()) discDone = true;
				}
			}
		}
		if (discDone && known.size() >= Balance.MAX_FIELD_CELLS) {
			finish(now);
			return spent;
		}
		while (discDone && !sweepDone && spent < readBudget) {
			if (frontier.isEmpty()) {
				finish(now);
				break;
			}
			Pos p = frontier.poll();
			if (!visited.add(p.pack())) continue;
			spent += read(v, p.x(), p.y(), p.z());
			if (known.size() >= Balance.MAX_FIELD_CELLS) finish(now);
		}
		return spent;
	}

	private void finish(long now) {
		sweepDone = true;
		sawUnloaded = unloadedThisSweep;
		sweepCompletedAt = now;
		known.keySet().removeIf(p -> !FieldReach.inSeed(composters, p) && !visited.contains(p.pack()));
	}

	private int read(FieldView v, int x, int y, int z) {
		FieldCell cell = v.cell(x, y, z);
		if (cell.kind() == FieldCell.Kind.UNLOADED) {
			unloadedThisSweep = true;
			return READS_PER_CELL;
		}
		store(new Pos(x, y, z), cell);
		return READS_PER_CELL;
	}

	/** Re-reads the indexed cells round-robin for at most {@code readBudget} block reads; returns the reads spent. */
	public int refresh(FieldView v, int readBudget) {
		int spent = 0;
		if (refreshAt >= refreshOrder.length) {
			refreshOrder = known.keySet().toArray(new Pos[0]);
			refreshAt = 0;
		}
		while (refreshAt < refreshOrder.length && spent + READS_PER_CELL <= readBudget) {
			Pos p = refreshOrder[refreshAt++];
			if (!known.containsKey(p)) continue;
			FieldCell cell = v.cell(p.x(), p.y(), p.z());
			spent += READS_PER_CELL;
			if (cell.kind() == FieldCell.Kind.UNLOADED) continue; // keep what is known until the chunk is back
			store(p, cell);
		}
		return spent;
	}

	/** Re-reads one cell now (the worker is about to act on it) and updates the index. */
	public FieldCell reread(FieldView v, Pos p) {
		FieldCell cell = v.cell(p.x(), p.y(), p.z());
		if (cell.kind() != FieldCell.Kind.UNLOADED && (known.containsKey(p) || FieldReach.inSeed(composters, p) || touches(p))) store(p, cell);
		return cell;
	}

	private boolean touches(Pos p) {
		for (int i = 0; i < 4; i++) for (int dy = -1; dy <= 1; dy++) {
			if (known.containsKey(new Pos(p.x() + STEP_X[i], p.y() + dy, p.z() + STEP_Z[i]))) return true;
		}
		return false;
	}

	private void store(Pos p, FieldCell cell) {
		if (cell.kind() == FieldCell.Kind.CROP || cell.kind() == FieldCell.Kind.EMPTY_FARMLAND) {
			if (!known.containsKey(p) && known.size() >= Balance.MAX_FIELD_CELLS) {
				capped = true;
				return;
			}
			boolean added = !known.containsKey(p);
			known.put(p, cell);
			if (added) enqueue(p);
		} else {
			known.remove(p);
		}
	}

	/** Farmland just outside the seed disc, so the disc sweep does not read it twice. */
	private void enqueue(Pos p) {
		for (int i = 0; i < 4; i++) for (int step = -1; step <= 1; step++) {
			Pos n = new Pos(p.x() + STEP_X[i], p.y() + step, p.z() + STEP_Z[i]);
			if (visited.contains(n.pack()) || FieldReach.inSeed(composters, n)) continue;
			frontier.add(n);
		}
	}

	/** The indexed cells in Pos order. A copy. */
	public List<Indexed> cells() {
		List<Indexed> out = new ArrayList<>(known.size());
		for (Map.Entry<Pos, FieldCell> e : known.entrySet()) out.add(new Indexed(e.getKey(), e.getValue()));
		return out;
	}

	public int size() { return known.size(); }
}
