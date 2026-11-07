package dev.villageheart.core.room;

/** Read-only world access for the scanner. Coordinates are absolute. */
public interface BlockView {
	Cell cell(int x, int y, int z);

	/**
	 * True when no SOLID cell (by this view's own {@link #cell}) is above (x, y, z) in that column, i.e. the position is outdoors.
	 * Must agree exactly with {@code cell()}; the leak rule relies on it. Only asked for OPEN cells.
	 */
	boolean openToSky(int x, int y, int z);

	default Cell cell(Pos p) { return cell(p.x(), p.y(), p.z()); }
}
