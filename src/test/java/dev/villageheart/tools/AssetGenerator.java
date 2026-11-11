package dev.villageheart.tools;

import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.building.ShapedSpec;
import dev.villageheart.core.sim.Json;
import dev.villageheart.core.worker.JobType;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * Generates every marker / job-block asset (blockstates, models, item definitions, loot tables, recipes,
 * advancements, textures, tags) from the core enums. Run with {@code ./gradlew genAssets}; the committed output is
 * checked byte-for-byte (JSON) and pixel-for-pixel (PNG) by GeneratedAssetsTest.
 */
public final class AssetGenerator {
	private AssetGenerator() {}

	public static final String NS = "villageheart";
	private static final String A = "assets/" + NS + "/";
	private static final String D = "data/" + NS + "/";
	public static final String PROFESSION_DIR = A + "textures/entity/villager/profession/";
	private static final String[] FACINGS = {"north", "east", "south", "west"};
	private static final int[] Y_ROT = {0, 90, 180, 270};

	/** Recipe id (path) to the item whose pickup unlocks it. */
	public static Map<String, String> unlockItems() {
		Map<String, String> m = new LinkedHashMap<>();
		m.put("blank_marker", "minecraft:item_frame");
		for (BuildingType t : BuildingType.values()) m.put(t.markerId(), NS + ":blank_marker");
		for (JobBlockType t : JobBlockType.values()) m.put(t.id(), NS + ":blank_marker");
		return m;
	}

	/** Recipes by result path. */
	public static Map<String, ShapedSpec> recipes() {
		Map<String, ShapedSpec> m = new LinkedHashMap<>();
		Map<Character, String> blankKey = new LinkedHashMap<>();
		blankKey.put('P', "#minecraft:planks");
		blankKey.put('F', "minecraft:item_frame");
		m.put("blank_marker", new ShapedSpec(List.of("PPP", "PFP", "PPP"), blankKey, NS + ":blank_marker", Balance.BLANK_MARKER_YIELD));
		for (BuildingType t : BuildingType.values()) m.put(t.markerId(), t.ring().recipe(NS + ":" + t.markerId(), 1));
		for (JobBlockType t : JobBlockType.values()) m.put(t.id(), t.recipe());
		return m;
	}

	/** All block ids (markers then job blocks). */
	public static List<String> blockIds() {
		List<String> ids = new ArrayList<>();
		for (BuildingType t : BuildingType.values()) ids.add(t.markerId());
		for (JobBlockType t : JobBlockType.values()) ids.add(t.id());
		return ids;
	}

	private static Map<String, Object> map(Object... kv) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
		return m;
	}

	private static byte[] json(Object o) { return Json.write(o, true).getBytes(StandardCharsets.UTF_8); }

	private static byte[] png(BufferedImage img) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<Object> list(Object... v) { return new ArrayList<>(List.of(v)); }

	private static Map<String, Object> itemDef(String model) {
		return map("model", map("type", "minecraft:model", "model", model));
	}

	private static Map<String, Object> face(String tex, Object uv) {
		return uv == null ? map("texture", tex) : map("texture", tex, "uv", uv);
	}

	private static Map<String, Object> allFaces(String tex) {
		return map("north", face(tex, null), "east", face(tex, null), "south", face(tex, null),
				"west", face(tex, null), "up", face(tex, null), "down", face(tex, null));
	}

	public static Map<String, byte[]> generate() {
		Map<String, byte[]> out = new TreeMap<>();

		// ---- marker plaque model template ----
		out.put(A + "models/block/marker_plaque.json", json(map(
				"textures", map("particle", "#front"),
				"elements", list(map(
						"from", list(1L, 1L, 15L), "to", list(15L, 15L, 16L),
						"faces", map(
								"north", map("texture", "#front", "uv", list(1L, 1L, 15L, 15L)),
								"east", face("#edge", null), "south", face("#edge", null),
								"west", face("#edge", null), "up", face("#edge", null), "down", face("#edge", null)))))));

		// ---- markers ----
		for (BuildingType t : BuildingType.values()) {
			String id = t.markerId();
			Map<String, Object> variants = new LinkedHashMap<>();
			for (int f = 0; f < 4; f++) {
				for (String status : MarkerArt.STATUSES) {
					Map<String, Object> v = map("model", NS + ":block/marker/" + t.id() + "_" + status);
					if (Y_ROT[f] != 0) v.put("y", (long) Y_ROT[f]);
					variants.put("facing=" + FACINGS[f] + ",status=" + status, v);
				}
			}
			out.put(A + "blockstates/" + id + ".json", json(map("variants", variants)));
			for (String status : MarkerArt.STATUSES) {
				String base = t.id() + "_" + status;
				out.put(A + "models/block/marker/" + base + ".json", json(map(
						"parent", NS + ":block/marker_plaque",
						"textures", map("front", NS + ":block/marker/" + base, "edge", NS + ":block/marker_edge"))));
				out.put(A + "textures/block/marker/" + base + ".png", png(MarkerArt.render(t, status)));
			}
			out.put(A + "items/" + id + ".json", json(itemDef(NS + ":item/" + id)));
			out.put(A + "models/item/" + id + ".json", json(map(
					"parent", "minecraft:item/generated",
					"textures", map("layer0", NS + ":block/marker/" + t.id() + "_unchecked"))));
		}

		// ---- blank marker item ----
		out.put(A + "items/blank_marker.json", json(itemDef(NS + ":item/blank_marker")));
		out.put(A + "models/item/blank_marker.json", json(map(
				"parent", "minecraft:item/generated", "textures", map("layer0", NS + ":item/blank_marker"))));
		out.put(A + "textures/item/blank_marker.png", png(JobBlockArt.blankMarker()));

		// ---- blueprint item (M20) ----
		out.put(A + "items/blueprint.json", json(itemDef(NS + ":item/blueprint")));
		out.put(A + "models/item/blueprint.json", json(map(
				"parent", "minecraft:item/generated", "textures", map("layer0", NS + ":item/blueprint"))));
		out.put(A + "textures/item/blueprint.png", png(BlueprintArt.render()));
		// drawn for blueprints-2; that branch's model still samples the blueprint sheet until it is retargeted
		out.put(A + "textures/item/blueprint_quill.png", png(ExtraArt.quill()));

		// ---- job blocks ----
		for (JobBlockType t : JobBlockType.values()) {
			Map<String, Object> variants = new LinkedHashMap<>();
			for (int f = 0; f < 4; f++) {
				Map<String, Object> v = map("model", NS + ":block/" + t.id());
				if (Y_ROT[f] != 0) v.put("y", (long) Y_ROT[f]);
				variants.put("facing=" + FACINGS[f], v);
			}
			out.put(A + "blockstates/" + t.id() + ".json", json(map("variants", variants)));
			out.put(A + "items/" + t.id() + ".json", json(itemDef(NS + ":block/" + t.id())));
		}
		for (Map.Entry<String, BufferedImage> e : JobBlockArt.textures().entrySet()) {
			out.put(A + "textures/block/" + e.getKey() + ".png", png(e.getValue()));
		}
		out.put(A + "models/block/chopping_block.json", json(map(
				"parent", "minecraft:block/block",
				"textures", map("particle", NS + ":block/chopping_block_side", "side", NS + ":block/chopping_block_side",
						"top", NS + ":block/chopping_block_top", "handle", NS + ":block/chopping_block_handle",
						"head", NS + ":block/chopping_block_head"),
				"elements", list(
						map("from", list(1L, 0L, 1L), "to", list(15L, 12L, 15L), "faces", map(
								"north", face("#side", null), "east", face("#side", null), "south", face("#side", null),
								"west", face("#side", null), "up", face("#top", null), "down", face("#top", null))),
						map("from", list(7.5, 10.0, 7.5), "to", list(8.5, 21.0, 8.5),
								"rotation", map("origin", list(8L, 12L, 8L), "axis", "z", "angle", 22.5),
								"faces", allFaces("#handle")),
						map("from", list(8.5, 16.0, 6.5), "to", list(12.5, 20.0, 9.5),
								"rotation", map("origin", list(8L, 12L, 8L), "axis", "z", "angle", 22.5),
								"faces", allFaces("#head"))))));
		out.put(A + "models/block/builders_table.json", json(map(
				"parent", "minecraft:block/block",
				"textures", map("particle", NS + ":block/builders_table_side", "top", NS + ":block/builders_table_top",
						"side", NS + ":block/builders_table_side", "front", NS + ":block/builders_table_front"),
				"elements", list(map("from", list(0L, 0L, 0L), "to", list(16L, 16L, 16L), "faces", map(
						"north", face("#front", null), "east", face("#side", null), "south", face("#side", null),
						"west", face("#side", null), "up", face("#top", null), "down", face("#side", null)))))));
		out.put(A + "models/block/market_stall.json", json(map(
				"parent", "minecraft:block/block",
				"textures", map("particle", NS + ":block/market_stall_side", "front", NS + ":block/market_stall_front",
						"side", NS + ":block/market_stall_side", "awning", NS + ":block/market_stall_awning"),
				"elements", list(
						map("from", list(0L, 0L, 0L), "to", list(16L, 12L, 16L), "faces", map(
								"north", face("#front", list(0L, 4L, 16L, 16L)), "east", face("#side", list(0L, 4L, 16L, 16L)),
								"south", face("#side", list(0L, 4L, 16L, 16L)), "west", face("#side", list(0L, 4L, 16L, 16L)),
								"down", face("#side", null))),
						map("from", list(0L, 12L, 0L), "to", list(16L, 16L, 16L), "faces", map(
								"north", face("#awning", list(0L, 0L, 16L, 4L)), "east", face("#awning", list(0L, 0L, 16L, 4L)),
								"south", face("#awning", list(0L, 0L, 16L, 4L)), "west", face("#awning", list(0L, 0L, 16L, 4L)),
								"up", face("#awning", null)))))));

		// ---- villager profession overlays (M7) ----
		for (JobType j : JobType.values()) out.put(PROFESSION_DIR + j.id() + ".png", png(ProfessionArt.render(j)));

		// ---- block faces that are not job-block enums ----
		out.put(A + "textures/block/marker_edge.png", png(ExtraArt.markerEdge()));
		out.put(A + "textures/block/village_heart.png", png(HeartBlockArt.render()));
		// blueprints-2 still points the scaffold model at bamboo; this is the texture to switch it to
		out.put(A + "textures/block/builder_scaffold.png", png(ExtraArt.scaffold()));

		// ---- GUI sprites and the refined icon (the mod icon itself stays the release-prep file) ----
		for (Map.Entry<String, BufferedImage> e : GuiArt.sprites().entrySet()) {
			out.put(A + e.getKey(), png(e.getValue()));
		}
		out.put(A + "icon_refined.png", png(ExtraArt.iconRefined()));

		// ---- loot tables ----
		for (String id : blockIds()) {
			out.put(D + "loot_table/blocks/" + id + ".json", json(map(
					"type", "minecraft:block",
					"pools", list(map("rolls", 1L,
							"entries", list(map("type", "minecraft:item", "name", NS + ":" + id)),
							"conditions", list(map("condition", "minecraft:survives_explosion")))))));
		}

		// ---- recipes and unlock advancements ----
		Map<String, String> unlock = unlockItems();
		for (Map.Entry<String, ShapedSpec> e : recipes().entrySet()) {
			String id = e.getKey();
			out.put(D + "recipe/" + id + ".json", json(e.getValue().toJson()));
			String item = unlock.get(id);
			String crit = "has_" + item.substring(item.indexOf(':') + 1);
			out.put(D + "advancement/recipes/misc/" + id + ".json", json(map(
					"parent", "minecraft:recipes/root",
					"criteria", map(
							crit, map("trigger", "minecraft:inventory_changed",
									"conditions", map("items", list(map("items", item)))),
							"has_the_recipe", map("trigger", "minecraft:recipe_unlocked",
									"conditions", map("recipe", NS + ":" + id))),
					"requirements", list(list("has_the_recipe", crit)),
					"rewards", map("recipes", list(NS + ":" + id)))));
		}

		// ---- tags ----
		List<Object> axe = new ArrayList<>();
		for (String id : blockIds()) axe.add(NS + ":" + id);
		out.put("data/minecraft/tags/block/mineable/axe.json", json(map("replace", false, "values", axe)));
		return out;
	}

	/** Existing files under {@code root} that this generator owns (so stale ones can be found). */
	public static List<String> ownedExisting(Path root) throws IOException {
		List<String> res = new ArrayList<>();
		if (!Files.isDirectory(root)) return res;
		List<String> jobs = new ArrayList<>();
		for (JobBlockType t : JobBlockType.values()) jobs.add(t.id());
		try (var stream = Files.walk(root)) {
			for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
				String rel = root.relativize(p).toString().replace('\\', '/');
				String name = p.getFileName().toString();
				String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
				boolean owned = rel.startsWith(PROFESSION_DIR) || rel.startsWith(A + "textures/block/marker/") || rel.startsWith(A + "models/block/marker/")
						|| rel.equals("data/minecraft/tags/block/mineable/axe.json")
						|| rel.equals(A + "models/block/marker_plaque.json");
				boolean named = base.endsWith("_marker") || base.equals("blank_marker") || base.equals("blueprint") || jobs.contains(base);
				if (!owned && named && (rel.startsWith(A + "blockstates/") || rel.startsWith(A + "items/")
						|| rel.startsWith(A + "models/item/") || rel.startsWith(A + "models/block/")
						|| rel.startsWith(D + "recipe/") || rel.startsWith(D + "loot_table/blocks/")
						|| rel.startsWith(D + "advancement/recipes/misc/"))) owned = true;
				if (!owned && rel.startsWith(A + "textures/")) {
					for (String j : jobs) if (base.equals(j) || base.startsWith(j + "_")) owned = true;
					if (rel.equals(A + "textures/item/blank_marker.png") || rel.equals(A + "textures/item/blueprint.png")
							|| rel.equals(A + "textures/item/blueprint_quill.png")) owned = true;
					if (rel.equals(A + "textures/block/marker_edge.png") || rel.equals(A + "textures/block/village_heart.png")
							|| rel.equals(A + "textures/block/builder_scaffold.png")) owned = true;
					if (rel.startsWith(A + "textures/gui/")) owned = true;
				}
				if (rel.equals(A + "icon_refined.png")) owned = true;
				if (owned) res.add(rel);
			}
		}
		return res;
	}

	public static void main(String[] args) throws IOException {
		Path root = Path.of(args[0]);
		Map<String, byte[]> files = generate();
		for (String stale : ownedExisting(root)) {
			if (!files.containsKey(stale)) Files.delete(root.resolve(stale));
		}
		for (Map.Entry<String, byte[]> e : files.entrySet()) {
			Path p = root.resolve(e.getKey());
			Files.createDirectories(p.getParent());
			Files.write(p, e.getValue());
		}
		System.out.println("Wrote " + files.size() + " files under " + root);
		if (args.length > 1) writePreview(Path.of(args[1]));
	}

	private static void blit(Graphics2D g, BufferedImage img, int x, int y, int scale) {
		g.drawImage(img, x, y, img.getWidth() * scale, img.getHeight() * scale, null);
	}

	/** Contact sheet: markers, job blocks, items and GUI icons, each at x8, plus the profession overlays. */
	static void writePreview(Path dir) throws IOException {
		Files.createDirectories(dir);
		List<String> names = new ArrayList<>();
		List<BufferedImage> imgs = new ArrayList<>();
		names.add("village_heart"); imgs.add(HeartBlockArt.render());
		names.add("blank_marker"); imgs.add(JobBlockArt.blankMarker());
		names.add("blueprint"); imgs.add(BlueprintArt.render());
		names.add("blueprint_quill"); imgs.add(ExtraArt.quill());
		names.add("scaffold"); imgs.add(ExtraArt.scaffold());
		names.add("marker_edge"); imgs.add(ExtraArt.markerEdge());
		for (Map.Entry<String, BufferedImage> e : JobBlockArt.textures().entrySet()) {
			names.add(e.getKey()); imgs.add(e.getValue());
		}
		for (BuildingType t : BuildingType.values()) {
			names.add(t.id()); imgs.add(MarkerArt.render(t, "valid"));
		}
		for (Map.Entry<String, BufferedImage> e : GuiArt.sprites().entrySet()) {
			String n = e.getKey();
			n = n.substring(n.lastIndexOf('/') + 1).replace(".png", "");
			names.add(n); imgs.add(e.getValue());
		}
		int scale = 8, gap = 6, label = 14, cols = 8;
		int cell = 16 * scale;
		int sheetW = cols * (cell + gap) + gap;
		int rows = (imgs.size() + cols - 1) / cols;
		int sheetH = rows * (cell + label + gap) + gap;
		BufferedImage sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(new Color(0x2A241C));
		g.fillRect(0, 0, sheetW, sheetH);
		g.setColor(Color.WHITE);
		for (int i = 0; i < imgs.size(); i++) {
			int cx = gap + (i % cols) * (cell + gap);
			int cy = gap + (i / cols) * (cell + label + gap);
			BufferedImage img = imgs.get(i);
			int dw = img.getWidth() * scale, dh = img.getHeight() * scale;
			g.drawImage(img, cx + (cell - dw) / 2, cy + (cell - dh) / 2, dw, dh, null);
			String labelText = names.get(i);
			if (labelText.length() > 18) labelText = labelText.substring(0, 18);
			g.drawString(labelText, cx, cy + cell + 12);
		}
		g.dispose();
		ImageIO.write(sheet, "png", dir.resolve("markers.png").toFile());
		ImageIO.write(sheet, "png", dir.resolve("contact.png").toFile());

		JobType[] jobs = JobType.values();
		int ps = 4, pt = 64 * ps, pgap = 8;
		BufferedImage prof = new BufferedImage(3 * (pt + pgap) + pgap, 3 * (pt + pgap) + pgap, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = prof.createGraphics();
		pg.setColor(new Color(0x7A5A3C));
		pg.fillRect(0, 0, prof.getWidth(), prof.getHeight());
		for (int k = 0; k < jobs.length; k++) {
			pg.drawImage(ProfessionArt.render(jobs[k]), pgap + (k % 3) * (pt + pgap), pgap + (k / 3) * (pt + pgap), pt, pt, null);
		}
		pg.dispose();
		ImageIO.write(prof, "png", dir.resolve("professions.png").toFile());

		// the refined icon beside a 4x copy of the block, so the two can be judged together
		BufferedImage icon = ExtraArt.iconRefined();
		BufferedImage pair = new BufferedImage(icon.getWidth() + 80, icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D ig = pair.createGraphics();
		ig.drawImage(icon, 0, 0, null);
		ig.drawImage(HeartBlockArt.render(), icon.getWidth() + 8, 32, 64, 64, null);
		ig.dispose();
		ImageIO.write(pair, "png", dir.resolve("icon_refined.png").toFile());
	}
}
