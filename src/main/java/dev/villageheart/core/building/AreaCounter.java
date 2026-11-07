package dev.villageheart.core.building;

import dev.villageheart.core.room.BlockView;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Pos;
import org.jetbrains.annotations.Nullable;

/** Counts cells carrying a content key inside a sphere (Hunting Ground, Training Yard). Pure, over a {@link BlockView}. */
public final class AreaCounter {
	private AreaCounter() {}

	/** @param notLoaded the first unloaded cell inside the sphere (the count is then incomplete), else null */
	public record AreaCount(int n, @Nullable Pos notLoaded) {}

	public static AreaCount count(BlockView v, Pos centre, int radius, String key) {
		int n = 0;
		long r2 = (long) radius * radius;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if ((long) dx * dx + (long) dy * dy + (long) dz * dz > r2) continue;
					Cell c = v.cell(centre.x() + dx, centre.y() + dy, centre.z() + dz);
					if (c.kind() == CellKind.UNLOADED) return new AreaCount(n, new Pos(centre.x() + dx, centre.y() + dy, centre.z() + dz));
					if (c.contents().contains(key)) n++;
				}
			}
		}
		return new AreaCount(n, null);
	}
}
