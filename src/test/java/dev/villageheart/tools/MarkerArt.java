package dev.villageheart.tools;

import dev.villageheart.core.building.BuildingType;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reproducible marker art: 16x16 plaque textures. Row/col 0 and 15 are transparent (outside the model's uv),
 * row/col 1 and 14 are the status frame, rows/cols 2..13 the background, and a 10x10 glyph sits in rows/cols 3..12.
 * Glyph characters: '.' background, '#' glyph colour, '+' secondary colour.
 */
public final class MarkerArt {
	private MarkerArt() {}

	public static final int FRAME_UNCHECKED = 0xFF8A8A8A;
	public static final int FRAME_INVALID = 0xFFC0392B;
	public static final int FRAME_VALID = 0xFF3FA34D;

	public static final String[] STATUSES = {"unchecked", "invalid", "valid"};
	public static final int[] FRAMES = {FRAME_UNCHECKED, FRAME_INVALID, FRAME_VALID};

	public record Art(String[] glyph, int bg, int main, int second) {}

	public static final Map<BuildingType, Art> ART = new EnumMap<>(BuildingType.class);

	private static void put(BuildingType t, int bg, int main, int second, String... glyph) {
		if (glyph.length != 10) throw new IllegalStateException(t + " glyph needs 10 rows");
		for (String r : glyph) if (r.length() != 10) throw new IllegalStateException(t + " row width: " + r);
		ART.put(t, new Art(glyph, 0xFF000000 | bg, 0xFF000000 | main, 0xFF000000 | second));
	}

	static {
		put(BuildingType.HOUSE, 0x1E3A5F, 0xFF6B4A, 0xF6EEDC,
				"....##....",
				"...####...",
				"..######..",
				".########.",
				"##########",
				".++++++++.",
				".+++..+++.",
				".+++..+++.",
				".+++..+++.",
				".+++..+++.");
		put(BuildingType.STORAGE, 0x4A2E1A, 0xE8B04A, 0xF6EEDC,
				"..........",
				".########.",
				"##########",
				"##########",
				"++++++++++",
				"##########",
				"####++####",
				"####++####",
				"##########",
				".########.");
		put(BuildingType.FARMHOUSE, 0x2F7D32, 0xF2D14B, 0xFFF3A8,
				".#..##..#.",
				".##.##.##.",
				".##.##.##.",
				"..#.##.#..",
				"...####...",
				"....++....",
				"...####...",
				"..##..##..",
				".##....##.",
				".#......#.");
		put(BuildingType.LUMBER_CAMP, 0x6B3F1F, 0xD7DCE0, 0xE8B04A,
				"....####..",
				"..#######.",
				"..#######.",
				"...######.",
				"....+###..",
				"...+......",
				"..+.......",
				".+........",
				"+.........",
				"..........");
		put(BuildingType.KITCHEN, 0xC8641A, 0x2A2220, 0xFFFFFF,
				"..+..+....",
				"...+..+...",
				"..+..+....",
				".########.",
				"##########",
				"#.######.#",
				"##########",
				".########.",
				"..#....#..",
				"..........");
		put(BuildingType.BUILDERS_HUT, 0x9C3B2E, 0xD7DCE0, 0xE8B04A,
				".########.",
				".########.",
				".########+",
				"....++++..",
				"....++....",
				"....++....",
				"....++....",
				"....++....",
				"....++....",
				"....++....");
		put(BuildingType.MINE, 0x5B4B7A, 0xE6EAEE, 0xE8B04A,
				"..######..",
				".##....##.",
				"##..++..##",
				"#...++...#",
				"....++....",
				"....++....",
				"....++....",
				"....++....",
				"....++....",
				"....++....");
		put(BuildingType.HUNTING_LODGE, 0x1F4D2B, 0xE8DCC0, 0xE8B04A,
				"..###.....",
				".#...#....",
				"#....#....",
				"#....#..+.",
				"+++++++++#",
				"#....#..+.",
				"#....#....",
				".#...#....",
				"..###.....",
				"..........");
		put(BuildingType.BARRACKS, 0x1B2B5E, 0xCDD3DA, 0xE8483A,
				"##########",
				"#++++++++#",
				"#+++##+++#",
				"#+++##+++#",
				"#+++##+++#",
				".#++##++#.",
				".#++##++#.",
				"..#+##+#..",
				"...####...",
				"....##....");
		put(BuildingType.MARKET, 0x0F4F52, 0x3DDC84, 0xCFFFE0,
				"....##....",
				"...####...",
				"..#++###..",
				".#+#######",
				"##########",
				".########.",
				"..######..",
				"...####...",
				"....##....",
				"..........");
		put(BuildingType.SMITHY, 0x2B2B2B, 0xC9CED4, 0xE8803A,
				"..........",
				"##########",
				"##########",
				".########.",
				"...####...",
				"...####...",
				"...####...",
				"..######..",
				".########.",
				"..........");
		put(BuildingType.MINESHAFT, 0x3A3F47, 0xC9CED4, 0xE8B04A,
				"...+.+....",
				"..+++++...",
				"##########",
				".########.",
				".########.",
				"..######..",
				"..#....#..",
				"..++..++..",
				"##########",
				".#.#.#.#.#");
		put(BuildingType.HUNTING_GROUND, 0x8A9A3C, 0x2B1F14, 0x1A120B,
				"...#..#...",
				"..##..##..",
				"..##..##..",
				"#..#..#..#",
				"##......##",
				"...####...",
				"..######..",
				"..######..",
				"...####...",
				"..........");
		put(BuildingType.TRAINING_YARD, 0xD9C28A, 0x2C3E50, 0x8A5A2B,
				"#........#",
				".#......#.",
				"..#....#..",
				"...#..#...",
				"....##....",
				"....##....",
				"..+#..#+..",
				".++#..#++.",
				"++......++",
				".+......+.");
	}

	public static BufferedImage render(BuildingType type, String status) {
		Art a = ART.get(type);
		int frame = FRAMES[java.util.Arrays.asList(STATUSES).indexOf(status)];
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y <= 14; y++) {
			for (int x = 1; x <= 14; x++) {
				boolean edge = x == 1 || x == 14 || y == 1 || y == 14;
				img.setRGB(x, y, edge ? frame : a.bg());
			}
		}
		for (int gy = 0; gy < 10; gy++) {
			for (int gx = 0; gx < 10; gx++) {
				char c = a.glyph()[gy].charAt(gx);
				if (c == '#') img.setRGB(3 + gx, 3 + gy, a.main());
				else if (c == '+') img.setRGB(3 + gx, 3 + gy, a.second());
			}
		}
		return img;
	}

	/** Glyph bitmap: true where the glyph (either colour) is drawn. */
	public static boolean[][] bitmap(BuildingType type) {
		String[] g = ART.get(type).glyph();
		boolean[][] b = new boolean[10][10];
		for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) b[y][x] = g[y].charAt(x) != '.';
		return b;
	}

	public static double luminance(int rgb) {
		double[] c = new double[3];
		int[] v = {(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255};
		for (int i = 0; i < 3; i++) {
			double s = v[i] / 255.0;
			c[i] = s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
	}

	public static double contrast(int a, int b) {
		double la = luminance(a), lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}
}
