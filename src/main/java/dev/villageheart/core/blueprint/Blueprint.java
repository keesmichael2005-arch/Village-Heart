package dev.villageheart.core.blueprint;

import dev.villageheart.core.Tier;
import dev.villageheart.core.room.Dir;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * A building plan in local coordinates: x to the right, z from the front (z = 0) to the back, layer {@code y} from the bottom.
 * The anchor cell (x, z) sits over the clicked ground block; layer 0 is {@code base} blocks above that block. {@code front} is the
 * direction the door faces in the local frame; a rotation turns it.
 *
 * @param buildsTypeId the building type whose room this makes ({@code house}), or null; such a blueprint has exactly one marker cell
 */
public record Blueprint(String id, String name, Tier tier, Kind kind, @Nullable String buildsTypeId, int base, int anchorX, int anchorZ,
		Dir front, int width, int height, int depth, Map<Character, PaletteEntry> palette, List<List<String>> layers, PlacementMode mode) {
	/** BUILDING (M20) or DEFENCE (M21). */
	public enum Kind { BUILDING, DEFENCE }

	public Blueprint {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(front, "front");
		Objects.requireNonNull(mode, "mode");
		palette = Map.copyOf(palette);
		List<List<String>> copy = new ArrayList<>();
		for (List<String> l : layers) copy.add(List.copyOf(l));
		layers = List.copyOf(copy);
	}

	public PaletteEntry entry(int x, int y, int z) { return palette.get(layers.get(y).get(z).charAt(x)); }

	/** The marker's palette entry, or null when the blueprint builds no room. */
	@Nullable
	public PaletteEntry markerEntry() {
		if (buildsTypeId == null) return null;
		String marker = "villageheart:" + buildsTypeId + "_marker";
		for (PaletteEntry e : palette.values()) if (e.material() != null && e.material().id().equals(marker)) return e;
		return null;
	}

	public boolean isMarker(PaletteEntry e) { return e == markerEntry(); }

	/** Cells that are not ANY. */
	public int touchedCount() {
		int n = 0;
		for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) if (entry(x, y, z).touched()) n++;
		return n;
	}

	/** Materials in order of first use: the main pass layer by layer, then attached things, then last things, the marker at the very end. */
	public MaterialList materials() {
		Map<String, Material> mats = new LinkedHashMap<>();
		Map<String, Integer> counts = new LinkedHashMap<>();
		PaletteEntry marker = markerEntry();
		for (PaletteEntry.Pass pass : PaletteEntry.Pass.values()) {
			for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
				PaletteEntry e = entry(x, y, z);
				if (!e.primary() || e.pass() != pass || e == marker) continue;
				mats.putIfAbsent(e.material().id(), e.material());
				counts.merge(e.material().id(), 1, Integer::sum);
			}
		}
		if (marker != null) {
			mats.putIfAbsent(marker.material().id(), marker.material());
			counts.merge(marker.material().id(), 1, Integer::sum);
		}
		List<MaterialList.Line> lines = new ArrayList<>();
		for (Map.Entry<String, Material> e : mats.entrySet()) lines.add(new MaterialList.Line(e.getValue(), counts.get(e.getKey())));
		return new MaterialList(lines);
	}

	/** Number of PLACE steps (primary cells), the marker included. */
	public int placeCount() {
		int n = 0;
		for (int y = 0; y < height; y++) for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) if (entry(x, y, z).primary()) n++;
		return n;
	}
}
