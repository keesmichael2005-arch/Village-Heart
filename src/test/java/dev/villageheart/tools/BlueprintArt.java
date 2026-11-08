package dev.villageheart.tools;

import java.awt.image.BufferedImage;

/** The Blueprint item texture: a sheet of blue paper with a white house glyph (16x16, transparent margin). */
public final class BlueprintArt {
	private BlueprintArt() {}

	private static final int PAPER = 0xFF2E5C9E, EDGE = 0xFF1B3A6B, LINE = 0xFFEAF2FF, CORNER = 0xFF4F82C8;

	public static BufferedImage render() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 1; y <= 14; y++) for (int x = 2; x <= 13; x++) {
			boolean edge = x == 2 || x == 13 || y == 1 || y == 14;
			img.setRGB(x, y, edge ? EDGE : PAPER);
		}
		// a folded corner
		img.setRGB(12, 2, CORNER);
		img.setRGB(11, 2, CORNER);
		img.setRGB(12, 3, CORNER);
		// the house: roof (triangle), walls, door
		int[][] roof = {{7, 4}, {8, 4}, {6, 5}, {9, 5}, {5, 6}, {10, 6}, {4, 7}, {11, 7}};
		for (int[] p : roof) img.setRGB(p[0], p[1], LINE);
		for (int x = 4; x <= 11; x++) img.setRGB(x, 7, LINE);
		for (int y = 8; y <= 12; y++) {
			img.setRGB(5, y, LINE);
			img.setRGB(10, y, LINE);
		}
		for (int x = 5; x <= 10; x++) img.setRGB(x, 12, LINE);
		for (int y = 10; y <= 11; y++) {
			img.setRGB(7, y, LINE);
			img.setRGB(8, y, LINE);
		}
		return img;
	}
}
