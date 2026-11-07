package dev.villageheart.core.room;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.RoomProblem.Kind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Room detection (DESIGN section 3.2): a flood fill of air starting just inside a marked door. Pure and stateless; the world is
 * seen through a {@link BlockView}. See docs/plans/M4.md for the pinned leak-localisation rule.
 */
public final class RoomScanner {
	private RoomScanner() {}

	/** Longest ceiling search above a cell. */
	private static final int CEILING_SEARCH = 64;

	/** The marker cell is always passable, whatever the view says, so a plaque can never plug or narrow an opening. */
	private record MarkerView(BlockView inner, long markerKey) implements BlockView {
		@Override
		public Cell cell(int x, int y, int z) { return Pos.pack(x, y, z) == markerKey ? Cell.OPEN : inner.cell(x, y, z); }

		@Override
		public boolean openToSky(int x, int y, int z) { return inner.openToSky(x, y, z); }
	}

	/** Scan the room behind the door that the marker at {@code marker} (hanging out of the wall towards {@code outward}) marks. */
	public static RoomScan scanFromMarker(BlockView raw, Pos marker, Dir outward) {
		BlockView view = new MarkerView(raw, marker.pack());
		Pos support = marker.offset(outward.opposite());
		Dir cw = outward.clockwise(), ccw = outward.counterClockwise();
		Pos[] candidates = {support.below(), support.offset(cw), support.offset(ccw), support.offset(cw).below(), support.offset(ccw).below()};
		for (Pos c : candidates) {
			Cell cell = view.cell(c);
			if (cell.kind() == CellKind.UNLOADED) return failed(marker, null, new RoomProblem(Kind.NOT_LOADED, c));
			if (cell.door() == DoorPart.NONE) continue;
			Pos lower = c;
			if (cell.door() == DoorPart.UPPER) {
				lower = c.below();
				Cell lc = view.cell(lower);
				if (lc.kind() == CellKind.UNLOADED) return failed(marker, null, new RoomProblem(Kind.NOT_LOADED, lower));
				if (lc.door() != DoorPart.LOWER) continue;
			}
			return scan(view, marker, lower, outward.opposite());
		}
		return failed(marker, null, new RoomProblem(Kind.NO_DOOR, marker));
	}

	/** Scan the room on the {@code inward} side of the door whose lower half is at {@code doorLower}. */
	public static RoomScan scanFromDoor(BlockView view, Pos doorLower, Dir inward) {
		return scan(view, null, doorLower, inward);
	}

	private static RoomScan failed(@Nullable Pos marker, @Nullable Pos door, RoomProblem problem) {
		return new RoomScan(marker, door, null, false, 0, 0, Map.of(), List.of(problem), Set.of(), Set.of(), Long.MAX_VALUE);
	}

	private static RoomScan scan(BlockView view, @Nullable Pos marker, Pos door, Dir inward) {
		Dir outward = inward.opposite();
		Pos s1 = door.offset(inward);
		Cell c1 = view.cell(s1);
		Pos start;
		Cell startCell;
		if (c1.kind() == CellKind.UNLOADED) return failed(marker, door, new RoomProblem(Kind.NOT_LOADED, s1));
		if (c1.kind() == CellKind.OPEN) {
			start = s1;
			startCell = c1;
		} else {
			Pos s2 = door.above().offset(inward);
			Cell c2 = view.cell(s2);
			if (c2.kind() == CellKind.UNLOADED) return failed(marker, door, new RoomProblem(Kind.NOT_LOADED, s2));
			if (c2.kind() != CellKind.OPEN) return failed(marker, door, new RoomProblem(Kind.DOORWAY_BLOCKED, s1));
			start = s2;
			startCell = c2;
		}

		Map<Long, Long> parent = new HashMap<>(); // interior cell to the cell it was reached from (start: itself)
		Set<Long> shell = new HashSet<>();
		Set<Long> recorded = new HashSet<>();
		Map<String, List<Pos>> contents = new LinkedHashMap<>();
		ArrayDeque<Long> queue = new ArrayDeque<>();

		long startKey = start.pack();
		parent.put(startKey, startKey);
		record(view, contents, recorded, start, startCell);
		RoomProblem problem = null;
		if (view.openToSky(start.x(), start.y(), start.z())) {
			problem = locateLeak(view, parent, door, outward, marker, start, start, null);
		} else {
			queue.add(startKey);
		}

		Dir[] dirs = Dir.values();
		fill:
		while (!queue.isEmpty()) {
			long ck = queue.poll();
			Pos cur = Pos.unpack(ck);
			for (Dir d : dirs) {
				int nx = cur.x() + d.dx, ny = cur.y() + d.dy, nz = cur.z() + d.dz;
				long nk = Pos.pack(nx, ny, nz);
				if (parent.containsKey(nk) || shell.contains(nk)) continue;
				Cell cell = view.cell(nx, ny, nz);
				Pos n = new Pos(nx, ny, nz);
				switch (cell.kind()) {
					case UNLOADED -> {
						problem = new RoomProblem(Kind.NOT_LOADED, n);
						break fill;
					}
					case SOLID -> {
						shell.add(nk);
						record(view, contents, recorded, n, cell);
					}
					case OPEN -> {
						if (view.openToSky(nx, ny, nz)) {
							problem = locateLeak(view, parent, door, outward, marker, start, n, cur);
							break fill;
						}
						parent.put(nk, ck);
						record(view, contents, recorded, n, cell);
						if (parent.size() > Balance.ROOM_MAX_AIR) {
							problem = new RoomProblem(Kind.TOO_BIG, null);
							break fill;
						}
						queue.add(nk);
					}
				}
			}
		}

		Set<Long> interior = parent.keySet();
		long roomKey = Long.MAX_VALUE;
		for (long k : interior) roomKey = Math.min(roomKey, k);
		return new RoomScan(marker, door, start, problem == null, interior.size(), floorArea(view, interior, shell), contents,
				problem == null ? List.of() : List.of(problem), interior, shell, roomKey);
	}

	private static void record(BlockView view, Map<String, List<Pos>> contents, Set<Long> recorded, Pos at, Cell cell) {
		if (recorded.add(at.pack())) add(contents, at, cell);
		Dir partner = cell.partner();
		if (partner != null) {
			Pos other = at.offset(partner);
			if (recorded.add(other.pack())) add(contents, other, view.cell(other));
		}
	}

	private static void add(Map<String, List<Pos>> contents, Pos at, Cell cell) {
		for (String key : cell.contents()) contents.computeIfAbsent(key, k -> new ArrayList<>()).add(at);
	}

	/** Distinct (x, z) columns holding at least one interior cell that stands on something that is not air. */
	private static int floorArea(BlockView view, Set<Long> interior, Set<Long> shell) {
		Set<Long> columns = new HashSet<>();
		for (long k : interior) {
			Pos p = Pos.unpack(k);
			long below = Pos.pack(p.x(), p.y() - 1, p.z());
			if (interior.contains(below)) continue;
			if (shell.contains(below) || view.cell(p.x(), p.y() - 1, p.z()).kind() != CellKind.OPEN) columns.add(Pos.pack(p.x(), 0, p.z()));
		}
		return columns.size();
	}

	// ---- leak localisation (docs/plans/M4.md 3.2) ----

	private static boolean nonOpen(BlockView view, Pos p) { return view.cell(p).kind() != CellKind.OPEN; }

	private static int blocked(BlockView view, Pos c) {
		int n = 0;
		for (Dir d : Dir.values()) if (d.horizontal() && nonOpen(view, c.offset(d))) n++;
		return n;
	}

	/** c has non-OPEN cells on both sides along at least one axis perpendicular to the travel direction t. */
	private static boolean constricted(BlockView view, Pos c, Dir t) {
		Dir[][] axes = {{Dir.WEST, Dir.EAST}, {Dir.DOWN, Dir.UP}, {Dir.NORTH, Dir.SOUTH}};
		for (Dir[] axis : axes) {
			if (t == axis[0] || t == axis[1]) continue;
			if (nonOpen(view, c.offset(axis[0])) && nonOpen(view, c.offset(axis[1]))) return true;
		}
		return false;
	}

	/**
	 * @param e the first discovered air cell that is open to the sky
	 * @param p the room cell it was reached from; null when the start cell itself is outdoors
	 */
	private static RoomProblem locateLeak(BlockView view, Map<Long, Long> parent, Pos door, Dir outward, @Nullable Pos marker, Pos start,
			Pos e, @Nullable Pos p) {
		List<Pos> path = new ArrayList<>(); // start .. e
		List<Dir> entered = new ArrayList<>(); // direction each path cell was entered by (null for start)
		if (p == null) {
			Pos markerSide = door.offset(outward);
			if (view.cell(markerSide).kind() == CellKind.OPEN && !view.openToSky(markerSide.x(), markerSide.y(), markerSide.z())) {
				return new RoomProblem(Kind.WRONG_SIDE, marker != null ? marker : markerSide);
			}
			Dir via = null;
			for (Dir d : Dir.values()) {
				if (!d.horizontal()) continue;
				Pos n = start.offset(d);
				if (view.cell(n).kind() == CellKind.OPEN && !view.openToSky(n.x(), n.y(), n.z())) {
					p = n;
					via = d.opposite();
					break;
				}
			}
			if (p == null) return new RoomProblem(Kind.ROOF_MISSING, start);
			path.add(e);
			entered.add(via);
		} else {
			Pos prev = e;
			Pos c = p;
			path.add(e);
			entered.add(directionBetween(c, prev));
			while (true) {
				long pk = parent.get(c.pack());
				path.add(c);
				if (pk == c.pack()) {
					entered.add(null);
					break;
				}
				Pos up = Pos.unpack(pk);
				entered.add(directionBetween(up, c));
				c = up;
			}
			Collections.reverse(path);
			Collections.reverse(entered);
		}

		// Roof test: the roof plane above a covered neighbour of the leak, at the leak's x/z. P first, then the other covered
		// neighbours of E, so a stepped (gable) roof finds the hole through the neighbour whose ceiling is at the hole's height.
		List<Pos> covered = new ArrayList<>();
		covered.add(p);
		for (Dir d : Dir.values()) {
			if (!d.horizontal()) continue;
			Pos q = e.offset(d);
			if (!q.equals(p) && view.cell(q).kind() == CellKind.OPEN && !view.openToSky(q.x(), q.y(), q.z())) covered.add(q);
		}
		for (Pos q : covered) {
			int ceiling = Integer.MIN_VALUE;
			for (int y = q.y() + 1; y <= q.y() + CEILING_SEARCH; y++) {
				if (nonOpen(view, new Pos(q.x(), y, q.z()))) {
					ceiling = y;
					break;
				}
			}
			if (ceiling == Integer.MIN_VALUE) continue;
			Pos h = new Pos(e.x(), ceiling, e.z());
			if (view.cell(h).kind() == CellKind.OPEN && blocked(view, h) >= 2) return new RoomProblem(Kind.ROOF_MISSING, h);
		}

		// Wall test: the last constricted cell on the path from the start to the leak.
		for (int i = path.size() - 1; i >= 0; i--) {
			Dir t = entered.get(i);
			if (t != null && constricted(view, path.get(i), t)) return new RoomProblem(Kind.WALL_GAP, path.get(i));
		}
		return new RoomProblem(Kind.WALL_GAP, p);
	}

	private static Dir directionBetween(Pos from, Pos to) {
		for (Dir d : Dir.values()) if (from.offset(d).equals(to)) return d;
		throw new IllegalStateException(from + " is not adjacent to " + to);
	}
}
