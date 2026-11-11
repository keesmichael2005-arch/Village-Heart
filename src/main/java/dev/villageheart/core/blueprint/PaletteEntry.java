package dev.villageheart.core.blueprint;

import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.DoorPart;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * One symbol of a blueprint's palette. {@code state} is a block state string ({@code minecraft:oak_door[facing=south,...]}) for BLOCK.
 * {@code partOf} marks the second half of a two-block unit: no material, placed with the primary one step in that direction.
 * {@code cell} and {@code contents} say how the room scanner sees the block, so core can prove a blueprint is a valid room; a GameTest pins
 * them against the real block states. {@code converts}: the material is not the block's own item (farmland from dirt, water from a bucket).
 */
public record PaletteEntry(char symbol, Kind kind, String state, @Nullable Material material, Pass pass, @Nullable Dir partOf,
		CellKind cell, List<String> contents, boolean converts) {
	public enum Kind { ANY, AIR, BLOCK }

	public enum Pass { MAIN, ATTACHED, LAST, DECOR }

	public PaletteEntry {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(pass, "pass");
		Objects.requireNonNull(cell, "cell");
		contents = List.copyOf(contents);
	}

	public static PaletteEntry any(char symbol) {
		return new PaletteEntry(symbol, Kind.ANY, "any", null, Pass.MAIN, null, CellKind.OPEN, List.of(), false);
	}

	public static PaletteEntry air(char symbol) {
		return new PaletteEntry(symbol, Kind.AIR, "air", null, Pass.MAIN, null, CellKind.OPEN, List.of(), false);
	}

	public boolean touched() { return kind != Kind.ANY; }

	public boolean isBlock() { return kind == Kind.BLOCK; }

	/** A BLOCK that has its own material: a step of its own (the other half of a unit is placed with it). */
	public boolean primary() { return kind == Kind.BLOCK && partOf == null; }

	/** The block id of {@code state}: {@code minecraft:oak_door}. */
	public String blockId() {
		int i = state.indexOf('[');
		return i < 0 ? state : state.substring(0, i);
	}

	/** The value of a property in {@code state}, or null. */
	@Nullable
	public String property(String name) {
		int i = state.indexOf('[');
		if (i < 0) return null;
		for (String kv : state.substring(i + 1, state.length() - 1).split(",")) {
			int e = kv.indexOf('=');
			if (e > 0 && kv.substring(0, e).equals(name)) return kv.substring(e + 1);
		}
		return null;
	}

	/** The scanner cell this entry makes (the same rule as the glue's {@code RoomCells}: the half that carries the contents). */
	public Cell scannerCell() {
		if (kind != Kind.BLOCK) return Cell.OPEN;
		Dir partner = null;
		if ("foot".equals(property("part"))) {
			String f = property("facing");
			if (f != null) partner = Dir.valueOf(f.toUpperCase(Locale.ROOT));
		} else if ("upper".equals(property("half"))) {
			partner = Dir.DOWN;
		}
		DoorPart door = DoorPart.NONE;
		if (blockId().endsWith("_door") && property("half") != null) door = "lower".equals(property("half")) ? DoorPart.LOWER : DoorPart.UPPER;
		List<String> keys = new ArrayList<>();
		if (partner == null) {
			keys.add(blockId());
			keys.addAll(contents);
		}
		return new Cell(cell, door, partner, keys);
	}
}
