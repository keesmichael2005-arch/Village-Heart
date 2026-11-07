package dev.villageheart.tools;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.building.BuildingType;
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
