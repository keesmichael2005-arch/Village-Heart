package dev.villageheart.core.room;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * What one block position means to the scanner. Instances are immutable and shared, so lookups allocate nothing.
 *
 * @param kind     OPEN: the fill passes. SOLID: wall/floor/roof/furniture, part of the shell. UNLOADED: not visible, the scan fails.
 * @param door     which half of a door this is (doors are always SOLID)
 * @param partner  for the half of a two-block thing that carries no contents (bed FOOT, door/plant UPPER), the direction to the
 *                 half that does
 * @param contents content keys (see {@link RoomContents}), de-duplicated for two-block things
 */
public record Cell(CellKind kind, DoorPart door, @Nullable Dir partner, List<String> contents) {
	public static final Cell OPEN = new Cell(CellKind.OPEN, DoorPart.NONE, null, List.of());
	public static final Cell SOLID = new Cell(CellKind.SOLID, DoorPart.NONE, null, List.of());
	public static final Cell UNLOADED = new Cell(CellKind.UNLOADED, DoorPart.NONE, null, List.of());

	public Cell {
		contents = List.copyOf(contents);
	}

	public static Cell open(String... contents) { return new Cell(CellKind.OPEN, DoorPart.NONE, null, List.of(contents)); }

	public static Cell solid(String... contents) { return new Cell(CellKind.SOLID, DoorPart.NONE, null, List.of(contents)); }

	public static Cell door(DoorPart part, String... contents) { return new Cell(CellKind.SOLID, part, null, List.of(contents)); }

	public Cell withPartner(Dir d) { return new Cell(kind, door, d, contents); }
}
