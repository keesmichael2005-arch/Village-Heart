package dev.villageheart.core.tree;

import dev.villageheart.core.room.Pos;

/** Read-only world access for the tree scanner. Coordinates are absolute. Must never load a chunk (reads of unloaded cells are UNLOADED). */
public interface TreeView {
	TreeCell cell(int x, int y, int z);

	/** y of the top non-air block of the column (the WORLD_SURFACE height), {@code Integer.MIN_VALUE} when the column is not loaded. */
	int top(int x, int z);

	/** True when a valid building's room (interior or shell) occupies the position. */
	boolean inRoom(Pos p);

	default TreeCell cell(Pos p) { return cell(p.x(), p.y(), p.z()); }
}
