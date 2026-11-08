package dev.villageheart.core.blueprint;

import dev.villageheart.core.Tier;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Dir;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the blueprint text format (docs/plans/M20.md 3.3):
 * <pre>
 * id small_house / name Small House / tier VILLAGE / kind BUILDING / builds house / base 1 / anchor 3 4 / front NORTH / [size 7 5 8]
 * palette
 * . any
 * _ air
 * P minecraft:oak_planks &lt;- #minecraft:planks "planks" "planks"
 * D minecraft:oak_door[...] &lt;- #minecraft:wooden_doors "door" cell=solid +#door
 * d minecraft:oak_door[...] part=DOWN
 * layer 0
 * ...rows (z = 0 first), one character per x...
 * end
 * </pre>
 * Blank lines and lines starting with {@code //} are skipped. A material has a name and an optional plural (default: name + "s").
 * Entry keys: {@code pass=main|attached|last}, {@code cell=open|solid}, {@code part=DIR}, {@code returns=item}, {@code converts},
 * {@code +content}. Any fault is a {@link BlueprintSyntaxException} with the line.
 */
public final class BlueprintParser {
	private BlueprintParser() {}

	/** The only entries whose material is not the block's own item. */
	public static final Set<String> CONVERSIONS = Set.of("minecraft:farmland<-minecraft:dirt", "minecraft:water<-minecraft:water_bucket");

	private record Tok(String text, boolean quoted) {}

	private static List<Tok> tokens(int line, String s) {
		List<Tok> out = new ArrayList<>();
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '"') {
				int end = s.indexOf('"', i + 1);
				if (end < 0) throw new BlueprintSyntaxException(line, "unterminated quote");
				out.add(new Tok(s.substring(i + 1, end), true));
				i = end + 1;
			} else {
				int j = i;
				while (j < s.length() && !Character.isWhitespace(s.charAt(j))) j++;
				out.add(new Tok(s.substring(i, j), false));
				i = j;
			}
		}
		return out;
	}

	public static Blueprint parse(String text) {
		String[] raw = text.split("\n", -1);
		Map<String, String> header = new LinkedHashMap<>();
		Map<Character, PaletteEntry> palette = new LinkedHashMap<>();
		List<List<String>> layers = new ArrayList<>();
		List<String> current = null;
		int[] size = null;
				int mode = 0; // 0 header, 1 palette, 2 layers
		for (int n = 0; n < raw.length; n++) {
			int line = n + 1;
			String s = raw[n].strip();
			if (s.isEmpty() || s.startsWith("//")) continue;
			if (mode == 0) {
				if (s.equals("palette")) {
					mode = 1;
					continue;
				}
				int sp = s.indexOf(' ');
				String key = sp < 0 ? s : s.substring(0, sp);
				String val = sp < 0 ? "" : s.substring(sp + 1).strip();
				switch (key) {
					case "id", "name", "tier", "kind", "builds", "base", "anchor", "front", "size", "mode" -> {
						if (header.putIfAbsent(key, val) != null) throw new BlueprintSyntaxException(line, "duplicate key " + key);
					}
					default -> throw new BlueprintSyntaxException(line, "unknown key " + key);
				}
			} else if (s.startsWith("layer ")) {
				mode = 2;
				int idx;
				try {
					idx = Integer.parseInt(s.substring(6).strip());
				} catch (NumberFormatException e) {
					throw new BlueprintSyntaxException(line, "bad layer number");
				}
				if (idx != layers.size()) throw new BlueprintSyntaxException(line, "layers must be in order, expected layer " + layers.size());
				current = new ArrayList<>();
				layers.add(current);
			} else if (s.equals("end")) {
				if (mode != 2) throw new BlueprintSyntaxException(line, "end before any layer");
				mode = 3;
			} else if (mode == 1) {
				PaletteEntry e = paletteLine(line, s);
				if (palette.putIfAbsent(e.symbol(), e) != null) throw new BlueprintSyntaxException(line, "duplicate symbol " + e.symbol());
			} else if (mode == 2) {
				current.add(s);
				for (int i = 0; i < s.length(); i++) {
					if (!palette.containsKey(s.charAt(i))) throw new BlueprintSyntaxException(line, "unknown symbol '" + s.charAt(i) + "'");
				}
			} else {
				throw new BlueprintSyntaxException(line, "text after end");
			}
		}
		if (mode != 3) throw new BlueprintSyntaxException(0, "missing end");
		return build(header, palette, layers);
	}

	private static PaletteEntry paletteLine(int line, String s) {
		List<Tok> t = tokens(line, s);
		if (t.size() < 2 || t.get(0).text().length() != 1) throw new BlueprintSyntaxException(line, "palette line needs a one-character symbol and a block");
		char sym = t.get(0).text().charAt(0);
		String state = t.get(1).text();
		if (state.equals("any") && t.size() == 2) return PaletteEntry.any(sym);
		if (state.equals("air") && t.size() == 2) return PaletteEntry.air(sym);
		int i = 2;
		Material material = null;
		String returns = null;
		if (i < t.size() && t.get(i).text().equals("<-") && !t.get(i).quoted()) {
			if (i + 2 >= t.size()) throw new BlueprintSyntaxException(line, "material needs an id and a name");
			String mid = t.get(i + 1).text();
			Tok name = t.get(i + 2);
			if (!name.quoted()) throw new BlueprintSyntaxException(line, "material name must be quoted");
			i += 3;
			String plural = name.text() + "s";
			if (i < t.size() && t.get(i).quoted()) plural = t.get(i++).text();
			material = new Material(mid, name.text(), plural, null);
		}
		PaletteEntry.Pass pass = PaletteEntry.Pass.MAIN;
		CellKind cell = null;
		Dir part = null;
		boolean converts = false;
		List<String> contents = new ArrayList<>();
		for (; i < t.size(); i++) {
			String x = t.get(i).text();
			if (x.equals("converts")) {
				converts = true;
			} else if (x.startsWith("+")) {
				contents.add(x.substring(1));
			} else if (x.startsWith("pass=")) {
				try {
					pass = PaletteEntry.Pass.valueOf(x.substring(5).toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new BlueprintSyntaxException(line, "unknown pass " + x.substring(5));
				}
			} else if (x.startsWith("cell=")) {
				cell = switch (x.substring(5)) {
					case "open" -> CellKind.OPEN;
					case "solid" -> CellKind.SOLID;
					default -> throw new BlueprintSyntaxException(line, "unknown cell kind " + x.substring(5));
				};
			} else if (x.startsWith("part=")) {
				try {
					part = Dir.valueOf(x.substring(5).toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new BlueprintSyntaxException(line, "unknown direction " + x.substring(5));
				}
			} else if (x.startsWith("returns=")) {
				returns = x.substring(8);
			} else {
				throw new BlueprintSyntaxException(line, "unknown palette option " + x);
			}
		}
		if (material != null && returns != null) material = new Material(material.id(), material.name(), material.plural(), returns);
		if (material == null && returns != null) throw new BlueprintSyntaxException(line, "returns needs a material");
		if (material == null && part == null) throw new BlueprintSyntaxException(line, "block " + state + " has no material (use <- or part=)");
		if (material != null && part != null) throw new BlueprintSyntaxException(line, "a part has no material of its own");
		PaletteEntry e = new PaletteEntry(sym, PaletteEntry.Kind.BLOCK, state, material, pass, part, cell == null ? CellKind.SOLID : cell,
				contents, converts);
		if (converts) {
			String key = e.blockId() + "<-" + (material == null ? "" : material.id());
			if (!CONVERSIONS.contains(key)) throw new BlueprintSyntaxException(line, "conversion not allowed: " + key);
		}
		return e;
	}

	private static String need(Map<String, String> h, String key) {
		String v = h.get(key);
		if (v == null) throw new BlueprintSyntaxException(0, "missing key " + key);
		return v;
	}

	private static int integer(String s, String what) {
		try {
			return Integer.parseInt(s.strip());
		} catch (NumberFormatException e) {
			throw new BlueprintSyntaxException(0, "bad number for " + what + ": " + s);
		}
	}

	private static Blueprint build(Map<String, String> h, Map<Character, PaletteEntry> palette, List<List<String>> layers) {
		String id = need(h, "id");
		if (!id.matches("[a-z][a-z0-9_]*")) throw new BlueprintSyntaxException(0, "id must be snake_case: " + id);
		String name = need(h, "name");
		Tier tier;
		try {
			tier = Tier.valueOf(need(h, "tier").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BlueprintSyntaxException(0, "unknown tier " + h.get("tier"));
		}
		Blueprint.Kind kind;
		try {
			kind = Blueprint.Kind.valueOf(need(h, "kind").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BlueprintSyntaxException(0, "unknown kind " + h.get("kind"));
		}
		PlacementMode mode = PlacementMode.FOOTPRINT;
		if (h.containsKey("mode")) {
			try {
				mode = PlacementMode.valueOf(h.get("mode").toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new BlueprintSyntaxException(0, "unknown mode " + h.get("mode"));
			}
		}
		String builds = need(h, "builds");
		@Nullable String buildsId = builds.equals("none") ? null : builds;
		int base = integer(need(h, "base"), "base");
		String[] a = need(h, "anchor").split("\\s+");
		if (a.length != 2) throw new BlueprintSyntaxException(0, "anchor needs x and z");
		int ax = integer(a[0], "anchor x"), az = integer(a[1], "anchor z");
		Dir front;
		try {
			front = Dir.valueOf(need(h, "front").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new BlueprintSyntaxException(0, "unknown front " + h.get("front"));
		}
		if (!front.horizontal()) throw new BlueprintSyntaxException(0, "front must be horizontal");
		if (layers.isEmpty()) throw new BlueprintSyntaxException(0, "no layers");
		int depth = layers.get(0).size();
		if (depth == 0) throw new BlueprintSyntaxException(0, "layer 0 has no rows");
		int width = layers.get(0).get(0).length();
		for (int y = 0; y < layers.size(); y++) {
			if (layers.get(y).size() != depth) throw new BlueprintSyntaxException(0, "layer " + y + " has " + layers.get(y).size() + " rows, expected " + depth);
			for (String row : layers.get(y)) {
				if (row.length() != width) throw new BlueprintSyntaxException(0, "ragged row in layer " + y + ": '" + row + "'");
			}
		}
		if (h.containsKey("size")) {
			String[] sz = h.get("size").split("\\s+");
			if (sz.length != 3 || integer(sz[0], "size") != width || integer(sz[1], "size") != layers.size() || integer(sz[2], "size") != depth) {
				throw new BlueprintSyntaxException(0, "size " + h.get("size") + " does not match the layers (" + width + " " + layers.size() + " " + depth + ")");
			}
		}
		if (ax < 0 || ax >= width || az < 0 || az >= depth) throw new BlueprintSyntaxException(0, "anchor (" + ax + ", " + az + ") is outside the footprint");
		Blueprint bp = new Blueprint(id, name, tier, kind, buildsId, base, ax, az, front, width, layers.size(), depth, palette, layers, mode);
		checkParts(bp);
		checkMarker(bp);
		return bp;
	}

	private static void checkParts(Blueprint bp) {
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			PaletteEntry e = bp.entry(x, y, z);
			if (e.partOf() == null) continue;
			Dir d = e.partOf();
			int px = x + d.dx, py = y + d.dy, pz = z + d.dz;
			boolean ok = px >= 0 && px < bp.width() && py >= 0 && py < bp.height() && pz >= 0 && pz < bp.depth();
			if (ok) {
				PaletteEntry p = bp.entry(px, py, pz);
				ok = p.primary() && p.blockId().equals(e.blockId());
			}
			if (!ok) throw new BlueprintSyntaxException(0, "part '" + e.symbol() + "' at (" + x + ", " + y + ", " + z + ") does not point at a primary of " + e.blockId());
		}
	}

	private static void checkMarker(Blueprint bp) {
		if (bp.buildsTypeId() == null) return;
		String marker = "villageheart:" + bp.buildsTypeId() + "_marker";
		int entries = 0, cells = 0;
		for (PaletteEntry e : bp.palette().values()) if (e.material() != null && e.material().id().equals(marker)) entries++;
		if (entries != 1) throw new BlueprintSyntaxException(0, "builds " + bp.buildsTypeId() + " needs exactly one marker entry (" + marker + "), found " + entries);
		PaletteEntry m = bp.markerEntry();
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) if (bp.entry(x, y, z) == m) cells++;
		if (cells != 1) throw new BlueprintSyntaxException(0, "the marker must be used in exactly one cell, found " + cells);
	}
}
