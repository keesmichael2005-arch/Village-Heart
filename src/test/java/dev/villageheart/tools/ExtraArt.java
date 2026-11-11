package dev.villageheart.tools;

import java.awt.image.BufferedImage;

/**
 * Textures that are not a marker or a job-block face: the plaque edge, the chopping-block materials, the
 * Blueprint Quill (drawn for the blueprints-2 branch, which still points its model at the blueprint sheet),
 * the scaffold cloth (drawn for that branch's cube, which still uses bamboo), and a refined mod icon kept
 * beside the release-prep icon rather than replacing it.
 */
public final class ExtraArt {
	private ExtraArt() {}

	/** Thin side of a marker plaque. A plain oak edge reads correctly when it is squashed onto a 1px face. */
	public static BufferedImage markerEdge() {
		BufferedImage img = ArtKit.image(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = (x == 0) ? ArtKit.OAK_HI : (x == 15) ? ArtKit.OAK_SH : ArtKit.vary(ArtKit.OAK_MD, x, y, 6);
				if (y % 4 == 3) c = ArtKit.darken(c, 0.8);
				img.setRGB(x, y, c);
			}
		}
		return img;
	}

	/** End grain: a bark rim, alternating growth rings, and two short axe scars. */
	public static BufferedImage choppingTop() {
		BufferedImage img = ArtKit.image(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double r = Math.hypot(x - 7.5, y - 7.5);
				int c;
				if (r > 7.15) c = ArtKit.OAK_SH;
				else c = ((int) (r * 1.7)) % 2 == 0 ? ArtKit.OAK_HI : ArtKit.OAK_DK;
				img.setRGB(x, y, c);
			}
		}
		for (int i = 0; i < 5; i++) {
			img.setRGB(4 + i, 6, ArtKit.OAK_SH);
			img.setRGB(5 + i, 7, ArtKit.INK);
			img.setRGB(7 + i, 10, ArtKit.OAK_SH);
		}
		return img;
	}

	/** Bark: vertical grain, a horizontal crack, and one knot. A diagonal speckle read as coarse dirt. */
	public static BufferedImage choppingSide() {
		BufferedImage img = ArtKit.image(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = switch (x % 4) {
					case 0 -> ArtKit.OAK_SH;
					case 1 -> ArtKit.OAK_DK;
					case 2 -> ArtKit.OAK;
					default -> ArtKit.OAK_HI;
				};
				if (y == 5 || y == 11) c = ArtKit.darken(c, 0.78);
				if (x >= 6 && x <= 9 && y >= 7 && y <= 10) c = ArtKit.OAK_MD;
				if (y == 0) c = ArtKit.lighten(c, 1.12);
				if (y == 15) c = ArtKit.darken(c, 0.72);
				img.setRGB(x, y, c);
			}
		}
		return img;
	}

	/** Handle texture: mostly one wood tone, because the model squashes it onto a thin cuboid. */
	public static BufferedImage choppingHandle() {
		BufferedImage img = ArtKit.image(16, 16);
		ArtKit.clear(img, ArtKit.OAK);
		for (int y = 0; y < 16; y++) {
			img.setRGB(0, y, ArtKit.OAK_HI);
			img.setRGB(1, y, ArtKit.OAK_HI);
			img.setRGB(14, y, ArtKit.OAK_DK);
			img.setRGB(15, y, ArtKit.OAK_SH);
			if (y % 5 == 4) for (int x = 2; x < 14; x++) img.setRGB(x, y, ArtKit.OAK_DK);
		}
		return img;
	}

	public static BufferedImage choppingHead() {
		BufferedImage img = ArtKit.image(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = (x + y < 6) ? ArtKit.IRON_HI : (x + y > 22) ? ArtKit.IRON_DK : ArtKit.IRON;
				img.setRGB(x, y, c);
			}
		}
		for (int i = 0; i < 16; i++) {
			img.setRGB(i, 0, ArtKit.IRON_HI);
			img.setRGB(i, 15, ArtKit.IRON_DK);
			img.setRGB(0, i, ArtKit.IRON_HI);
			img.setRGB(15, i, ArtKit.IRON_DK);
		}
		return img;
	}

	/**
	 * Feather, gold nib, a drop of blueprint ink. The blueprints-2 model still samples {@code villageheart:item/blueprint};
	 * this file is the texture to point {@code layer0} at when that branch merges.
	 */
	public static BufferedImage quill() {
		return ArtKit.icon16(
				"................",
				"......wwwo......",
				".....wWWWo......",
				"....wWWWo.......",
				"...wWWWo........",
				"..wWWWo.........",
				".wWWWo..........",
				"wWWo............",
				"aWo.............",
				".aC.............",
				"..aC............",
				"...aG...........",
				"....oG..........",
				".....og.........",
				"......bB........",
				"................");
	}

	/**
	 * Crossed pale poles on a dark gap. The blueprints-2 scaffold model is still {@code minecraft:block/bamboo_block};
	 * retarget {@code all} here when that branch merges.
	 */
	public static BufferedImage scaffold() {
		BufferedImage img = ArtKit.image(16, 16);
		ArtKit.clear(img, ArtKit.OAK_SH);
		for (int i = 0; i < 16; i++) {
			pole(img, i, i);
			pole(img, i, 15 - i);
			if (i % 5 == 0) for (int y = 0; y < 16; y++) pole(img, i, y);
			if (i % 5 == 2) for (int x = 0; x < 16; x++) pole(img, x, i);
		}
		ArtKit.bevel(img);
		return img;
	}

	private static void pole(BufferedImage img, int x, int y) {
		if (x < 0 || y < 0 || x > 15 || y > 15) return;
		img.setRGB(x, y, (x + y) % 2 == 0 ? ArtKit.OAK_HI : ArtKit.OAK);
	}

	/**
	 * A cleaner 128x128 heart-over-a-bell in the same palette as {@code icon.png}. Not referenced by
	 * {@code fabric.mod.json}; the release-prep icon stays the mod icon.
	 */
	public static BufferedImage iconRefined() {
		int n = 128;
		BufferedImage img = ArtKit.image(n, n);
		ArtKit.clear(img, ArtKit.NAVY);
		boolean[][] bell = new boolean[n][n];
		boolean[][] yoke = new boolean[n][n];
		boolean[][] heart = new boolean[n][n];
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if (y >= 18 && y <= 30 && x >= 34 && x <= 94) yoke[y][x] = true;
				if (y >= 30 && y <= 40 && ((x >= 46 && x <= 52) || (x >= 76 && x <= 82))) yoke[y][x] = true;
				double t = (y - 40) / 62.0;
				if (t >= 0 && t <= 1) {
					double half = 16 + t * 30;
					if (Math.abs(x - 64) <= half) bell[y][x] = true;
				}
				if (y >= 96 && y <= 108 && Math.abs(x - 64) <= 48) bell[y][x] = true;
			}
		}
		stampHeart(heart);
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if (outline(yoke, x, y) || outline(bell, x, y)) img.setRGB(x, y, ArtKit.INK);
			}
		}
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if (yoke[y][x] && img.getRGB(x, y) != ArtKit.INK) {
					int c = x < 40 || y < 22 ? ArtKit.OAK_HI : x > 88 ? ArtKit.OAK_DK : ArtKit.OAK;
					img.setRGB(x, y, c);
				} else if (bell[y][x] && !heart[y][x] && img.getRGB(x, y) != ArtKit.INK) {
					int band = x < 50 ? 0 : x < 64 ? 1 : x < 84 ? 2 : 3;
					int c = switch (band) {
						case 0 -> ArtKit.GOLD_HI;
						case 1 -> ArtKit.GOLD;
						case 2 -> ArtKit.GOLD_MID;
						default -> ArtKit.GOLD_DK;
					};
					if (y > 98) c = ArtKit.darken(c, 0.78);
					else if (y < 48) c = ArtKit.lighten(c, 1.06);
					img.setRGB(x, y, c);
				}
			}
		}
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				if (!heart[y][x]) continue;
				boolean edge = outline(heart, x, y);
				if (edge) img.setRGB(x, y, ArtKit.HEART_DK);
				else if (x + y < 130) img.setRGB(x, y, ArtKit.HEART_HI);
				else if (x + y > 160) img.setRGB(x, y, ArtKit.HEART_DK);
				else img.setRGB(x, y, ArtKit.HEART);
			}
		}
		// clapper
		for (int y = 100; y <= 112; y++) for (int x = 60; x <= 68; x++) {
			if ((x - 64) * (x - 64) + (y - 106) * (y - 106) < 22) img.setRGB(x, y, ArtKit.GOLD_OL);
		}
		return img;
	}

	/** A vanilla-style heart, six pixels tall, scaled so it sits on the bell face. */
	private static void stampHeart(boolean[][] heart) {
		String[] rows = {".##..##.", "########", "########", ".######.", "..####..", "...##..."};
		int scale = 6;
		int ox = 64 - rows[0].length() * scale / 2;
		int oy = 58;
		for (int y = 0; y < rows.length; y++) {
			for (int x = 0; x < rows[y].length(); x++) {
				if (rows[y].charAt(x) != '#') continue;
				for (int dy = 0; dy < scale; dy++) {
					for (int dx = 0; dx < scale; dx++) heart[oy + y * scale + dy][ox + x * scale + dx] = true;
				}
			}
		}
	}

	private static boolean outline(boolean[][] shape, int x, int y) {
		int n = shape.length;
		if (x < 0 || y < 0 || x >= n || y >= n || !shape[y][x]) return false;
		for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
			int xx = x + dx, yy = y + dy;
			if (xx < 0 || yy < 0 || xx >= n || yy >= n || !shape[yy][xx]) return true;
		}
		return false;
	}
}
