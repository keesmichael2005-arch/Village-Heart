package dev.villageheart.core.defence;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The village's defence cells, rebuilt when {@code defences} changes (docs/plans/M25.md 3.9). Not saved.
 * The first site to claim a cell keeps it.
 */
public final class DefenceIndex {
	private final Map<Long, DefenceCell> cells;
	private final Map<Long, List<Long>> byChunk;

	private DefenceIndex(Map<Long, DefenceCell> cells, Map<Long, List<Long>> byChunk) {
		this.cells = cells;
		this.byChunk = byChunk;
	}

	public static DefenceIndex of(List<DefenceSite> sites) {
		Map<Long, DefenceCell> cells = new HashMap<>();
		Map<Long, List<Long>> byChunk = new HashMap<>();
		for (DefenceSite site : sites) {
			for (DefenceCells.Placed placed : DefenceCells.of(site)) {
				long packed = placed.pos().pack();
				if (cells.containsKey(packed)) continue;
				cells.put(packed, new DefenceCell(site.id(), packed, placed.kind(), placed.expectedState()));
				byChunk.computeIfAbsent(chunkKey(placed.pos().x() >> 4, placed.pos().z() >> 4), k -> new ArrayList<>()).add(packed);
			}
		}
		return new DefenceIndex(cells, byChunk);
	}

	public int size() { return cells.size(); }

	public DefenceCell get(long packed) { return cells.get(packed); }

	public Collection<DefenceCell> cells() { return cells.values(); }

	/** Defence cells whose centre is within {@code radius} blocks of {@code (x, y, z)} (Euclidean, block coordinates). */
	public List<DefenceCell> near(int x, int y, int z, int radius) {
		int r2 = radius * radius;
		int cx0 = (x - radius) >> 4, cx1 = (x + radius) >> 4;
		int cz0 = (z - radius) >> 4, cz1 = (z + radius) >> 4;
		List<DefenceCell> out = new ArrayList<>();
		for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
			List<Long> ps = byChunk.get(chunkKey(cx, cz));
			if (ps == null) continue;
			for (long p : ps) {
				Pos pos = Pos.unpack(p);
				int dx = pos.x() - x, dy = pos.y() - y, dz = pos.z() - z;
				if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= r2) out.add(cells.get(p));
			}
		}
		return out;
	}

	private static long chunkKey(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }
}
