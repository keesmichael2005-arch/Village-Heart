package dev.villageheart.tools;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.worker.JobType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class GeneratedAssetsTest {
	private static Path root() {
		String dir = System.getProperty("villageheart.resourcesDir");
		assertNotNull(dir, "system property villageheart.resourcesDir must be set (build.gradle test task)");
		Path p = Path.of(dir);
		assertTrue(Files.isDirectory(p), "villageheart.resourcesDir is not a directory: " + dir);
		return p;
	}

	@Test
	void committedFilesMatchGenerator() throws IOException {
		Path root = root();
		Map<String, byte[]> generated = AssetGenerator.generate();
		for (Map.Entry<String, byte[]> e : generated.entrySet()) {
			Path f = root.resolve(e.getKey());
			assertTrue(Files.isRegularFile(f), "missing generated file (run ./gradlew genAssets): " + e.getKey());
			byte[] actual = Files.readAllBytes(f);
			if (e.getKey().endsWith(".png")) {
				BufferedImage a = ImageIO.read(new ByteArrayInputStream(actual)), b = ImageIO.read(new ByteArrayInputStream(e.getValue()));
				assertEquals(b.getWidth(), a.getWidth(), e.getKey());
				assertEquals(b.getHeight(), a.getHeight(), e.getKey());
				for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
					assertEquals(b.getRGB(x, y), a.getRGB(x, y), e.getKey() + " pixel " + x + "," + y + " (run ./gradlew genAssets)");
				}
			} else {
				assertArrayEquals(e.getValue(), actual, "stale generated file (run ./gradlew genAssets): " + e.getKey());
			}
		}
		for (String owned : AssetGenerator.ownedExisting(root)) {
			assertTrue(generated.containsKey(owned), "orphan generator-owned file (run ./gradlew genAssets): " + owned);
		}
	}

	/** The nine textures no model uses live in docs/art-staging, and match the generator. */
	@Test
	void stagedFilesMatchGenerator() throws IOException {
		Path staging = root().getParent().getParent().getParent().resolve("docs/art-staging");
		Map<String, byte[]> staged = AssetGenerator.staged();
		assertEquals(9, staged.size());
		for (Map.Entry<String, byte[]> e : staged.entrySet()) {
			Path f = staging.resolve(e.getKey());
			assertTrue(Files.isRegularFile(f), "missing staged file (run ./gradlew genAssets): " + e.getKey());
			BufferedImage a = ImageIO.read(f.toFile());
			BufferedImage b = ImageIO.read(new ByteArrayInputStream(e.getValue()));
			assertEquals(b.getWidth(), a.getWidth(), e.getKey());
			assertEquals(b.getHeight(), a.getHeight(), e.getKey());
			for (int y = 0; y < b.getHeight(); y++) for (int x = 0; x < b.getWidth(); x++) {
				assertEquals(b.getRGB(x, y), a.getRGB(x, y), e.getKey() + " pixel " + x + "," + y);
			}
		}
		try (var stream = Files.walk(staging)) {
			for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
				String rel = staging.relativize(p).toString().replace('\\', '/');
				assertTrue(staged.containsKey(rel), "staged file the generator does not claim: " + rel);
			}
		}
	}

	@Test
	void guiOutlinesAreLightAndFoodIsALoaf() {
		for (Map<String, BufferedImage> sprites : java.util.List.of(GuiArt.sprites(), GuiArt.stagedSprites())) {
			for (Map.Entry<String, BufferedImage> e : sprites.entrySet()) {
				BufferedImage img = e.getValue();
				for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
					assertNotEquals(ArtKit.INK, img.getRGB(x, y), e.getKey() + " still uses ink at " + x + "," + y);
				}
			}
		}
		BufferedImage food = GuiArt.sprites().get("textures/gui/sprites/heart/need_food.png");
		int minX = food.getWidth(), maxX = 0, minY = food.getHeight(), maxY = 0;
		for (int y = 0; y < food.getHeight(); y++) for (int x = 0; x < food.getWidth(); x++) {
			if ((food.getRGB(x, y) >>> 24) == 0) continue;
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minY = Math.min(minY, y);
			maxY = Math.max(maxY, y);
		}
		assertTrue(maxX - minX >= (maxY - minY) + 4, "food need should be a wide loaf, not a coin");
	}

	private static boolean[][] mirror(boolean[][] b) {
		boolean[][] m = new boolean[10][10];
		for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) m[y][x] = b[y][9 - x];
		return m;
	}

	private static int diff(boolean[][] a, boolean[][] b) {
		int n = 0;
		for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) if (a[y][x] != b[y][x]) n++;
		return n;
	}

	@Test
	void professionOverlaysExistAndAreReadable() throws IOException {
		JobType[] jobs = JobType.values();
		for (int i = 0; i < jobs.length; i++) {
			Path f = root().resolve(AssetGenerator.PROFESSION_DIR + jobs[i].id() + ".png");
			assertTrue(Files.isRegularFile(f), "missing profession texture " + f);
			BufferedImage img = ImageIO.read(f.toFile());
			assertEquals(64, img.getWidth(), jobs[i].id());
			assertEquals(64, img.getHeight(), jobs[i].id());
			assertTrue(img.getColorModel().hasAlpha(), jobs[i].id() + " has an alpha channel");
			int opaque = 0, transparent = 0;
			for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
				int a = img.getRGB(x, y) >>> 24;
				assertTrue(a == 0 || a == 255, jobs[i].id() + " pixel alpha " + a);
				if (a == 255) opaque++; else transparent++;
			}
			assertTrue(opaque > 300 && transparent > 2000, jobs[i].id() + " opaque=" + opaque + " transparent=" + transparent);
			ProfessionArt.Art art = ProfessionArt.ART.get(jobs[i]);
			assertTrue(MarkerArt.contrast(art.glyph(), art.apron()) >= 3.0, jobs[i] + " glyph contrast " + MarkerArt.contrast(art.glyph(), art.apron()));
			assertTrue(MarkerArt.contrast(art.sash(), art.apron()) >= 1.5, jobs[i] + " sash contrast");
			for (int j = i + 1; j < jobs.length; j++) {
				assertNotEquals(art.apron(), ProfessionArt.ART.get(jobs[j]).apron(), jobs[i] + " and " + jobs[j] + " share an apron colour");
				int d = 0;
				boolean[][] a = ProfessionArt.bitmap(jobs[i]), b = ProfessionArt.bitmap(jobs[j]);
				for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) if (a[y][x] != b[y][x]) d++;
				assertTrue(d >= 6, jobs[i] + " vs " + jobs[j] + " glyph distance " + d);
			}
		}
	}

	@Test
	void texturesReadable() {
		BuildingType[] types = BuildingType.values();
		for (int i = 0; i < types.length; i++) {
			MarkerArt.Art art = MarkerArt.ART.get(types[i]);
			assertTrue(MarkerArt.contrast(art.main(), art.bg()) >= 3.0, types[i] + " glyph contrast " + MarkerArt.contrast(art.main(), art.bg()));
			assertTrue(MarkerArt.contrast(art.second(), art.bg()) >= 3.0, types[i] + " secondary contrast " + MarkerArt.contrast(art.second(), art.bg()));
			for (int j = 0; j < types.length; j++) {
				if (i == j) continue;
				boolean[][] a = MarkerArt.bitmap(types[i]), b = MarkerArt.bitmap(types[j]);
				assertTrue(diff(a, b) >= 15, types[i] + " vs " + types[j] + ": " + diff(a, b));
				assertTrue(diff(a, mirror(b)) >= 15, types[i] + " vs mirror of " + types[j] + ": " + diff(a, mirror(b)));
			}
			BufferedImage base = MarkerArt.render(types[i], "unchecked");
			for (int s = 0; s < 3; s++) {
				BufferedImage img = MarkerArt.render(types[i], MarkerArt.STATUSES[s]);
				assertEquals(16, img.getWidth());
				assertEquals(16, img.getHeight());
				for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
					int argb = img.getRGB(x, y);
					boolean margin = x == 0 || x == 15 || y == 0 || y == 15;
					boolean frame = !margin && (x == 1 || x == 14 || y == 1 || y == 14);
					if (margin) assertEquals(0, argb >>> 24, "margin transparent " + types[i] + " " + x + "," + y);
					else assertEquals(255, argb >>> 24, "opaque " + types[i] + " " + x + "," + y);
					if (frame) assertEquals(MarkerArt.FRAMES[s], argb, "frame colour");
					else assertEquals(base.getRGB(x, y), argb, "icon changed with status: " + types[i] + " " + x + "," + y);
				}
			}
		}
		Set<Integer> frames = new HashSet<>();
		for (int f : MarkerArt.FRAMES) frames.add(f);
		assertEquals(3, frames.size());
		for (Map.Entry<String, BufferedImage> e : JobBlockArt.textures().entrySet()) {
			BufferedImage img = e.getValue();
			assertEquals(16, img.getWidth(), e.getKey());
			assertEquals(16, img.getHeight(), e.getKey());
			for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) assertEquals(255, img.getRGB(x, y) >>> 24, e.getKey() + " opaque");
		}
	}
}
