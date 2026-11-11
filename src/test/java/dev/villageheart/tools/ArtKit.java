package dev.villageheart.tools;

import java.awt.image.BufferedImage;

/**
 * Shared pixel helpers and the Village Heart palette. Colours are taken from the mod icon (a heart set into a
 * brass bell): the same gold, heart-red, oak and ink everywhere, so items, blocks and GUI icons read as one set.
 * Shading follows vanilla blocks: light on the top and left, dark on the bottom and right, flat bands rather than
 * gradients. Nothing here is copied from a vanilla texture.
 */
public final class ArtKit {
	private ArtKit() {}

	public static final int INK = 0xFF120C10;
	public static final int NAVY = 0xFF1B283A;
	public static final int NAVY_LT = 0xFF24344A;

	public static final int GOLD_HI = 0xFFF6D87A;
	public static final int GOLD = 0xFFD6A434;
	public static final int GOLD_MID = 0xFFB07C24;
	public static final int GOLD_DK = 0xFF704816;
	public static final int GOLD_SH = 0xFF54301A;
	public static final int GOLD_OL = 0xFF2A1C12;

	public static final int HEART_HI = 0xFFF48480;
	public static final int HEART = 0xFFC62A36;
	public static final int HEART_DK = 0xFF6E1220;

	public static final int OAK_HI = 0xFFD4B07A;
	public static final int OAK = 0xFFBA844A;
	public static final int OAK_MD = 0xFFA06E38;
	public static final int OAK_DK = 0xFF8A562E;
	public static final int OAK_SH = 0xFF5C3A22;

	public static final int IRON_HI = 0xFFF4F7FA;
	public static final int IRON = 0xFFC5CED6;
	public static final int IRON_MD = 0xFF8E99A4;
	public static final int IRON_DK = 0xFF5A6570;

	public static final int CREAM = 0xFFF4F0E4;
	public static final int CREAM_DK = 0xFFE4D8C0;

	public static final int EM_HI = 0xFFB6F5A0;
	public static final int EM = 0xFF3CB54A;
	public static final int EM_DK = 0xFF1E6E28;

	public static final int BLUE_HI = 0xFF8AA8D4;
	public static final int BLUE = 0xFF3E6298;
	public static final int BLUE_DK = 0xFF243E68;
	public static final int BLUE_LN = 0xFFE7F0FF;

	public static final int STONE = 0xFF8E9298;
	public static final int STONE_DK = 0xFF4E545C;

	public static BufferedImage image(int w, int h) {
		return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
	}

	public static void clear(BufferedImage img, int argb) {
		for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, argb);
	}

	public static boolean plot(BufferedImage img, int ox, int oy, String[] rows) {
		for (int y = 0; y < rows.length; y++) {
			String row = rows[y];
			for (int x = 0; x < row.length(); x++) {
				int c = ink(row.charAt(x));
				if (c != 0 && ox + x >= 0 && oy + y >= 0 && ox + x < img.getWidth() && oy + y < img.getHeight()) {
					img.setRGB(ox + x, oy + y, c);
				}
			}
		}
		return true;
	}

	/** Palette letter used by every hand-drawn sprite. {@code .} is left untouched. */
	public static int ink(char c) {
		return switch (c) {
			case '.', ' ' -> 0;
			case 'o' -> INK;
			case 'g' -> GOLD_HI;
			case 'G' -> GOLD;
			case 'M' -> GOLD_MID;
			case 'D' -> GOLD_DK;
			case 'S' -> GOLD_SH;
			case 'r' -> HEART_HI;
			case 'R' -> HEART;
			case 'k' -> HEART_DK;
			case 'w' -> CREAM;
			case 'W' -> CREAM_DK;
			case 'e' -> EM_HI;
			case 'E' -> EM;
			case 'd' -> EM_DK;
			case 'b' -> BLUE_HI;
			case 'B' -> BLUE;
			case 'n' -> BLUE_DK;
			case 'l' -> BLUE_LN;
			case 'i' -> IRON_HI;
			case 'I' -> IRON;
			case 's' -> IRON_MD;
			case 'z' -> IRON_DK;
			case 'y' -> GOLD_HI;
			case 'Y' -> GOLD_MID;
			case 'p' -> 0xFF7CC85A;
			case 'P' -> 0xFF2E7A28;
			case 'c' -> OAK_HI;
			case 'C' -> OAK;
			case 'a' -> OAK_DK;
			case 'A' -> OAK_SH;
			case 't' -> STONE;
			case 'T' -> STONE_DK;
			case 'u' -> NAVY;
			case 'U' -> NAVY_LT;
			default -> throw new IllegalStateException("unknown art ink '" + c + "'");
		};
	}

	public static int lighten(int argb, double f) { return scale(argb, f); }

	public static int darken(int argb, double f) { return scale(argb, f); }

	public static int scale(int argb, double f) {
		int a = (argb >>> 24) & 0xFF;
		int r = clamp((int) (((argb >> 16) & 0xFF) * f));
		int g = clamp((int) (((argb >> 8) & 0xFF) * f));
		int b = clamp((int) ((argb & 0xFF) * f));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** A tiny deterministic wobble so a flat field is not one solid colour. {@code amt} is about 0..12. */
	public static int vary(int argb, int x, int y, int amt) {
		int h = ((x * 13 + y * 29) * 17) & 7;
		int d = (h - 3) * Math.max(1, amt) / 3;
		return shift(argb, d);
	}

	public static int shift(int argb, int d) {
		int a = (argb >>> 24) & 0xFF;
		int r = clamp(((argb >> 16) & 0xFF) + d);
		int g = clamp(((argb >> 8) & 0xFF) + d);
		int b = clamp((argb & 0xFF) + d);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

	/** Oak boards: four horizontal planks, joints staggered, highlight on the top of each board. */
	public static BufferedImage planks() {
		BufferedImage img = image(16, 16);
		for (int band = 0; band < 4; band++) {
			int y0 = band * 4;
			int seam = (band * 5 + 3) % 15;
			for (int y = y0; y < y0 + 4; y++) {
				for (int x = 0; x < 16; x++) {
					int c = y == y0 ? OAK_HI : y == y0 + 3 ? OAK_DK : vary(OAK, x, y, 10);
					if (x == seam) c = OAK_SH;
					img.setRGB(x, y, c);
				}
			}
		}
		return img;
	}

	/** Darken the bottom and right edges and lighten the top and left, the way a vanilla cube is shaded. */
	public static void bevel(BufferedImage img) {
		int w = img.getWidth(), h = img.getHeight();
		for (int i = 0; i < w; i++) {
			img.setRGB(i, 0, lighten(img.getRGB(i, 0), 1.15));
			img.setRGB(i, h - 1, darken(img.getRGB(i, h - 1), 0.62));
		}
		for (int i = 0; i < h; i++) {
			img.setRGB(0, i, lighten(img.getRGB(0, i), 1.08));
			img.setRGB(w - 1, i, darken(img.getRGB(w - 1, i), 0.7));
		}
		img.setRGB(0, h - 1, darken(img.getRGB(0, h - 1), 0.85));
		img.setRGB(w - 1, 0, darken(img.getRGB(w - 1, 0), 0.9));
	}

	public static BufferedImage sprite(String... rows) {
		int h = rows.length, w = rows[0].length();
		for (String row : rows) if (row.length() != w) throw new IllegalStateException("sprite row width " + row.length() + " in '" + row + "'");
		BufferedImage img = image(w, h);
		plot(img, 0, 0, rows);
		return img;
	}

	/** A 16x16 item or block face. Fails loudly if a row was miscounted. */
	public static BufferedImage icon16(String... rows) {
		if (rows.length != 16) throw new IllegalStateException("icon height " + rows.length);
		for (String row : rows) if (row.length() != 16) throw new IllegalStateException(row.length() + ": " + row);
		return sprite(rows);
	}
}
