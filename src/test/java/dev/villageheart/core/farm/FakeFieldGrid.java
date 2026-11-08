package dev.villageheart.core.farm;

import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** A sparse test world of crop cells (unset = OTHER). Counts block reads like LevelFieldView: 2 per cell. */
final class FakeFieldGrid implements FieldView {
	final Map<Pos, FieldCell> cells = new HashMap<>();
	final Set<Long> unloadedColumns = new HashSet<>();
	long reads;

	static long col(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

	void set(int x, int y, int z, FieldCell c) { cells.put(new Pos(x, y, z), c); }

	void unload(int x, int z) { unloadedColumns.add(col(x, z)); }

	void load(int x, int z) { unloadedColumns.remove(col(x, z)); }

	/** A square field of crops with a water centre (OTHER), as in the roadmap test. */
	void field(int cx, int y, int cz, int half, FieldCell crop) {
		for (int x = cx - half; x <= cx + half; x++) for (int z = cz - half; z <= cz + half; z++) {
			if (x == cx && z == cz) continue;
			set(x, y, z, crop);
		}
	}

	@Override
	public FieldCell cell(int x, int y, int z) {
		reads += FieldIndex.READS_PER_CELL;
		if (unloadedColumns.contains(col(x, z))) return FieldCell.UNLOADED;
		return cells.getOrDefault(new Pos(x, y, z), FieldCell.OTHER);
	}
}
