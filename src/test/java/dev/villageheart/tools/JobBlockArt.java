package dev.villageheart.tools;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Job-block textures (16x16, fully opaque). Chopping Block uses vanilla log textures and has none here. */
public final class JobBlockArt {
	private JobBlockArt() {}

	private static final int OAK = 0xFFA97C45, OAK_DARK = 0xFF8A6334, OAK_LIGHT = 0xFFBF9355;
	private static final int PAPER = 0xFF2F5FA3, LINE = 0xFFE9F1FF, LINE_DIM = 0xFF6F94C9;
	private static final int RED = 0xFFC8382E, WHITE = 0xFFF4F0E6;

	private static BufferedImage fill(int argb) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, argb);
		return img;
	}

	private static BufferedImage planks() {
		BufferedImage img = fill(OAK);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				if (y % 4 == 3) img.setRGB(x, y, OAK_DARK);
				else if (y % 4 == 0 && (x + y / 4 * 5) % 7 != 0) img.setRGB(x, y, OAK_LIGHT);
			}
		}
		return img;
	}

	private static void rect(BufferedImage img, int x0, int y0, int x1, int y1, int argb) {
		for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) img.setRGB(x, y, argb);
	}

	public static Map<String, BufferedImage> textures() {
		Map<String, BufferedImage> m = new LinkedHashMap<>();

		// Builder's Table: top is a blueprint sheet with a grid, a drawn plan and a ruler.
		BufferedImage top = fill(PAPER);
		for (int i = 0; i < 16; i += 4) for (int j = 0; j < 16; j++) { top.setRGB(i, j, LINE_DIM); top.setRGB(j, i, LINE_DIM); }
		rect(top, 3, 3, 11, 9, LINE);
		rect(top, 4, 4, 10, 8, PAPER);
		for (int x = 4; x <= 10; x++) top.setRGB(x, 6, LINE);
		for (int y = 4; y <= 8; y++) top.setRGB(7, y, LINE);
		rect(top, 0, 13, 15, 15, 0xFFE8B04A);
		for (int x = 0; x < 16; x += 2) top.setRGB(x, 13, 0xFF6B4A12);
		m.put("builders_table_top", top);

		BufferedImage side = planks();
		m.put("builders_table_side", side);

		BufferedImage front = planks();
		// hanging hammer and saw
		rect(front, 3, 2, 6, 3, 0xFFB8BEC6);
		rect(front, 4, 4, 5, 11, 0xFF6B4A12);
		rect(front, 9, 3, 12, 4, 0xFFB8BEC6);
		rect(front, 9, 5, 12, 11, 0xFFD7DCE0);
		for (int y = 5; y <= 11; y += 2) front.setRGB(12, y, 0xFF8A9099);
		rect(front, 9, 12, 12, 13, 0xFF6B4A12);
		m.put("builders_table_front", front);

		// Market Stall: counter with crates and produce; striped awning.
		BufferedImage mfront = planks();
		rect(mfront, 1, 3, 7, 13, 0xFF7A5226);
		rect(mfront, 2, 4, 6, 7, 0xFFC8382E);
		rect(mfront, 2, 9, 6, 12, 0xFFE0A030);
		rect(mfront, 9, 3, 14, 13, 0xFF7A5226);
		rect(mfront, 10, 4, 13, 7, 0xFF3DDC84);
		rect(mfront, 10, 9, 13, 12, 0xFF8FBF3A);
		m.put("market_stall_front", mfront);
		m.put("market_stall_side", planks());

		BufferedImage awning = fill(WHITE);
		for (int x = 0; x < 16; x++) if ((x / 2) % 2 == 0) for (int y = 0; y < 16; y++) awning.setRGB(x, y, RED);
		m.put("market_stall_awning", awning);
		return m;
	}

	/** The Blank Marker item (16x16, with a transparent margin). */
	public static BufferedImage blankMarker() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y <= 14; y++) for (int x = 1; x <= 14; x++) {
			boolean edge = x == 1 || x == 14 || y == 1 || y == 14;
			img.setRGB(x, y, edge ? 0xFF8A8A8A : 0xFFB89B6A);
		}
		for (int y = 4; y <= 11; y++) for (int x = 4; x <= 11; x++) {
			boolean e = x == 4 || x == 11 || y == 4 || y == 11;
			if (e) img.setRGB(x, y, 0xFF7A5226);
		}
		return img;
	}
}
