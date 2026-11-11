package dev.villageheart.tools;

import java.awt.image.BufferedImage;

/** The Village Heart block face: a brass cube with the mod's heart inlaid. Same gold and red as the bell icon. */
public final class HeartBlockArt {
	private HeartBlockArt() {}

	public static BufferedImage render() {
		BufferedImage img = ArtKit.image(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c;
				if (y == 0 || x == 0) c = ArtKit.GOLD_HI;
				else if (y == 15 || x == 15) c = ArtKit.GOLD_SH;
				else if (y == 1 || x == 1) c = ArtKit.lighten(ArtKit.GOLD, 1.12);
				else if (y == 14 || x == 14) c = ArtKit.GOLD_DK;
				else c = ArtKit.vary(ArtKit.GOLD, x, y, 8);
				img.setRGB(x, y, c);
			}
		}
		img.setRGB(0, 15, ArtKit.GOLD_DK);
		img.setRGB(15, 0, ArtKit.GOLD_MID);
		img.setRGB(15, 15, ArtKit.GOLD_OL);
		// the heart, outlined in ink so it stays readable on the brass
		ArtKit.plot(img, 3, 3, new String[] {
				".oo....oo.",
				"oorr..rroo",
				"orRRRRRRRo",
				"oRRRRRRRRo",
				"oRRRRRRRRk",
				".oRRRRRRk.",
				"..oRRRRk..",
				"...oRRk...",
				"....ok...."
		});
		return img;
	}
}
