package dev.villageheart.core.room;

import java.util.HashMap;
import java.util.Map;

/** Sparse test world: every unset position is OPEN. Keeps a per-column "highest non-OPEN y" so sky checks are cheap and never stale. */
public final class FakeGrid implements BlockView {
	/** position to palette index (0 = OPEN); a primitive map so the perf test measures the scanner, not boxing. */
	private final LongMap cells = new LongMap(1024);
	private final java.util.List<Cell> palette = new java.util.ArrayList<>(java.util.List.of(Cell.OPEN));
	private final Map<Cell, Integer> paletteIndex = new HashMap<>(Map.of(Cell.OPEN, 0));
	/** column key (x,z) to highest y holding a non-OPEN cell; absent when none. */
	private final Map<Long, Integer> top = new HashMap<>();
	private int unloadedBeyondX = Integer.MAX_VALUE;

	private static long col(int x, int z) { return Pos.pack(x, 0, z); }

	public FakeGrid unloadedBeyondX(int limit) { this.unloadedBeyondX = limit; return this; }

	public FakeGrid set(int x, int y, int z, Cell c) {
		setNoRefresh(x, y, z, c);
		refreshColumn(x, z);
		return this;
	}

	public FakeGrid clear(int x, int y, int z) { return set(x, y, z, Cell.OPEN); }

	private int minY = Integer.MAX_VALUE;

	private void refreshColumn(int x, int z) {
		// walk down from the old top to the next non-OPEN cell; only called after a cell in this column changed
		Integer old = top.get(col(x, z));
		int from = old == null ? maxY : Math.max(old, maxY);
		Integer best = null;
		for (int y = from; y >= minY; y--) {
			if (cell(x, y, z).kind() != CellKind.OPEN) { best = y; break; }
		}
		if (best == null) top.remove(col(x, z)); else top.put(col(x, z), best);
	}

	private int maxY = Integer.MIN_VALUE;

	/** Solid box, inclusive corners. */
	public FakeGrid box(int x0, int y0, int z0, int x1, int y1, int z1, Cell c) {
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) setNoRefresh(x, y, z, c);
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) refreshColumn(x, z);
		return this;
	}

	/** Shell of {@code wall} (floor, walls, roof) with OPEN inside. */
	public FakeGrid hollow(int x0, int y0, int z0, int x1, int y1, int z1, Cell wall) {
		for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
			boolean edge = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
			setNoRefresh(x, y, z, edge ? wall : Cell.OPEN);
		}
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) refreshColumn(x, z);
		return this;
	}

	private void setNoRefresh(int x, int y, int z, Cell c) {
		long k = Pos.pack(x, y, z);
		minY = Math.min(minY, y);
		maxY = Math.max(maxY, y);
		int idx = paletteIndex.computeIfAbsent(c, cc -> { palette.add(cc); return palette.size() - 1; });
		cells.put(k, idx);
	}

	/** Door with its lower half at (x, y, z). */
	public FakeGrid door(int x, int y, int z) {
		set(x, y, z, Cell.door(DoorPart.LOWER, "minecraft:oak_door", RoomContents.DOOR));
		return set(x, y + 1, z, Cell.door(DoorPart.UPPER).withPartner(Dir.DOWN));
	}

	/** Standard fixture: 5x5x3 interior x,z 1..5, y 1..3 (75 air), door (3,1,0)/(3,2,0). Marker (3,3,-1), outward NORTH. */
	public static FakeGrid room5x5x3() {
		FakeGrid g = new FakeGrid();
		g.hollow(0, 0, 0, 6, 4, 6, Cell.solid("minecraft:stone"));
		g.door(3, 1, 0);
		return g;
	}

	public static final Pos MARKER = new Pos(3, 3, -1);

	@Override
	public Cell cell(int x, int y, int z) {
		if (x > unloadedBeyondX) return Cell.UNLOADED;
		return palette.get((int) cells.getOrDefault(Pos.pack(x, y, z), 0));
	}

	@Override
	public boolean openToSky(int x, int y, int z) {
		Integer t = top.get(col(x, z));
		return t == null || t < y;
	}
}
