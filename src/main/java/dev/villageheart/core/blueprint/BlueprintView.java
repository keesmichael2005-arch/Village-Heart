package dev.villageheart.core.blueprint;

import dev.villageheart.core.room.BlockView;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Pos;

/**
 * The finished blueprint at a placement, as the room scanner would see it: solid ground up to and including the ground block, the
 * blueprint's cells (by their {@link PaletteEntry#scannerCell()}), air everywhere else. Proves in plain Java that a blueprint is a valid
 * room of its type in every rotation.
 */
public final class BlueprintView implements BlockView {
	private final Blueprint bp;
	private final Placement placement;
	private final int top;

	public BlueprintView(Blueprint bp, Placement placement) {
		this.bp = bp;
		this.placement = placement;
		this.top = placement.ground().y() + bp.base() + bp.height() - 1;
	}

	@Override
	public Cell cell(int x, int y, int z) {
		PaletteEntry e = placement.entryAt(bp, new Pos(x, y, z));
		if (e != null && e.touched()) return e.scannerCell();
		return y <= placement.ground().y() ? Cell.SOLID : Cell.OPEN;
	}

	@Override
	public boolean openToSky(int x, int y, int z) {
		for (int yy = y + 1; yy <= top; yy++) if (cell(x, yy, z).kind() == CellKind.SOLID) return false;
		return true;
	}
}
