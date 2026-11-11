package dev.villageheart.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.sim.Json;
import dev.villageheart.core.worker.JobType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every texture a model or a screen actually names must be a file in the jar. A miss is the purple-and-black
 * missing texture. Minecraft's own textures are not checked.
 */
class ReferencedTexturesTest {
	private static final Pattern JAVA_PNG = Pattern.compile("textures/[a-z0-9_./]+\\.png");
	private static final Pattern JAVA_SPRITE_PREFIX = Pattern.compile("textures/gui/sprites/[a-z0-9_./]*");

	private static Path resources() {
		String dir = System.getProperty("villageheart.resourcesDir");
		assertTrue(dir != null && Files.isDirectory(Path.of(dir)), "villageheart.resourcesDir");
		return Path.of(dir);
	}

	@Test
	void everyReferencedTextureExists() throws IOException {
		Path root = resources();
		Path project = root.getParent().getParent().getParent();
		List<String> missing = new ArrayList<>();

		try (Stream<Path> files = Files.walk(root.resolve("assets/villageheart"))) {
			files.filter(p -> p.toString().endsWith(".json")).forEach(p -> checkJson(root, p, missing));
		}
		String modJson = Files.readString(root.resolve("fabric.mod.json"));
		Object parsed = Json.parse(modJson);
		if (parsed instanceof Map<?, ?> map && map.get("icon") instanceof String icon && !icon.isBlank()) {
			if (!Files.isRegularFile(root.resolve(icon))) missing.add("fabric.mod.json icon " + icon);
		}
		for (Path src : List.of(project.resolve("src/main/java"), project.resolve("src/client/java"))) {
			if (!Files.isDirectory(src)) continue;
			try (Stream<Path> files = Files.walk(src)) {
				files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
					try {
						String text = Files.readString(p);
						Matcher m = JAVA_PNG.matcher(text);
						while (m.find()) {
							String rel = "assets/villageheart/" + m.group();
							if (!Files.isRegularFile(root.resolve(rel))) missing.add(p.getFileName() + " -> " + rel);
						}
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
			}
		}
		// sprites built from a job or building id (the prefix is a literal; the id is not)
		for (JobType job : JobType.values()) {
			String rel = "assets/villageheart/textures/gui/sprites/heart/job_" + job.id() + ".png";
			if (!Files.isRegularFile(root.resolve(rel))) missing.add(rel);
		}
		for (BuildingType type : BuildingType.values()) {
			String rel = "assets/villageheart/textures/gui/sprites/heart/building_" + type.id() + ".png";
			if (!Files.isRegularFile(root.resolve(rel))) missing.add(rel);
		}
		assertTrue(missing.isEmpty(), "missing textures:\n" + String.join("\n", missing));
		// the prefix scan above must have seen the sprite directory literal, or a rename would skip the java check
		String textures = Files.readString(project.resolve("src/client/java/dev/villageheart/client/art/ModTextures.java"));
		assertTrue(JAVA_SPRITE_PREFIX.matcher(textures).find(), "ModTextures no longer names textures/gui/sprites/");
	}

	/**
	 * A PNG under {@code textures/} that nothing names is dead weight in the jar. References are model JSON,
	 * a {@code textures/...png} literal, the job and building sprite ids, and the profession overlay path
	 * Minecraft loads from the profession id ({@code textures/entity/villager/profession/<id>.png}).
	 */
	@Test
	void noOrphanTextures() throws IOException {
		Path root = resources();
		Path project = root.getParent().getParent().getParent();
		Set<String> referenced = new HashSet<>();
		try (Stream<Path> files = Files.walk(root.resolve("assets/villageheart"))) {
			files.filter(p -> p.toString().endsWith(".json")).forEach(p -> collectJson(p, referenced));
		}
		for (Path src : List.of(project.resolve("src/main/java"), project.resolve("src/client/java"))) {
			if (!Files.isDirectory(src)) continue;
			try (Stream<Path> files = Files.walk(src)) {
				files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
					try {
						Matcher m = JAVA_PNG.matcher(Files.readString(p));
						while (m.find()) referenced.add(m.group());
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				});
			}
		}
		for (JobType job : JobType.values()) {
			referenced.add("textures/gui/sprites/heart/job_" + job.id() + ".png");
			referenced.add("textures/entity/villager/profession/" + job.id() + ".png");
		}
		for (BuildingType type : BuildingType.values()) {
			referenced.add("textures/gui/sprites/heart/building_" + type.id() + ".png");
		}
		List<String> orphans = new ArrayList<>();
		Path textures = root.resolve("assets/villageheart/textures");
		try (Stream<Path> files = Files.walk(textures)) {
			files.filter(p -> p.toString().endsWith(".png")).forEach(p -> {
				String rel = textures.getParent().relativize(p).toString().replace('\\', '/');
				if (!referenced.contains(rel)) orphans.add(rel);
			});
		}
		assertTrue(orphans.isEmpty(), "textures nothing references:\n" + String.join("\n", orphans));
	}

	private static void collectJson(Path file, Set<String> referenced) {
		Object parsed;
		try {
			parsed = Json.parse(Files.readString(file));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		collect(parsed, referenced);
	}

	private static void collect(Object node, Set<String> referenced) {
		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> e : map.entrySet()) {
				String key = String.valueOf(e.getKey());
				Object value = e.getValue();
				if (value instanceof String s && s.startsWith("villageheart:") && !key.equals("parent") && !key.equals("model")) {
					referenced.add("textures/" + s.substring("villageheart:".length()) + ".png");
				} else {
					collect(value, referenced);
				}
			}
		} else if (node instanceof List<?> list) {
			for (Object value : list) collect(value, referenced);
		}
	}

	private static void checkJson(Path root, Path file, List<String> missing) {
		Object parsed;
		try {
			parsed = Json.parse(Files.readString(file));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		walk(root, file.getFileName().toString(), parsed, missing);
	}

	@SuppressWarnings("unchecked")
	private static void walk(Path root, String from, Object node, List<String> missing) {
		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> e : map.entrySet()) {
				String key = String.valueOf(e.getKey());
				Object value = e.getValue();
				if (value instanceof String s && s.startsWith("villageheart:")) {
					String path = s.substring("villageheart:".length());
					boolean model = key.equals("parent") || key.equals("model");
					String rel = model ? "assets/villageheart/models/" + path + ".json" : "assets/villageheart/textures/" + path + ".png";
					if (!Files.isRegularFile(root.resolve(rel))) missing.add(from + " " + key + " " + s + " -> " + rel);
				} else {
					walk(root, from, value, missing);
				}
			}
		} else if (node instanceof List<?> list) {
			for (Object value : list) walk(root, from, value, missing);
		}
	}
}
