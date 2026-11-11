package dev.villageheart.tools;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Job-block textures, 16x16 and fully opaque. Chopping-block faces are original; the model no longer borrows vanilla logs. */
public final class JobBlockArt {
	private JobBlockArt() {}

	public static Map<String, BufferedImage> textures() {
		Map<String, BufferedImage> m = new LinkedHashMap<>();
		m.put("builders_table_top", tableTop());
		m.put("builders_table_side", tableSide());
		m.put("builders_table_front", tableFront());
		m.put("market_stall_front", stallFront());
		m.put("market_stall_side", stallSide());
		m.put("market_stall_awning", awning());
		m.put("chopping_block_top", ExtraArt.choppingTop());
		m.put("chopping_block_side", ExtraArt.choppingSide());
		m.put("chopping_block_handle", ExtraArt.choppingHandle());
		m.put("chopping_block_head", ExtraArt.choppingHead());
		return m;
	}

	private static BufferedImage tableTop() {
		BufferedImage img = ArtKit.planks();
		// a sheet of blueprint paper in a wood frame, with a drawn plan
		for (int y = 2; y <= 12; y++) for (int x = 2; x <= 13; x++) img.setRGB(x, y, ArtKit.BLUE);
		for (int i = 3; i <= 12; i++) {
			img.setRGB(i, 2, ArtKit.BLUE_HI);
			img.setRGB(2, i, ArtKit.BLUE_HI);
			img.setRGB(i, 12, ArtKit.BLUE_DK);
			img.setRGB(13, i, ArtKit.BLUE_DK);
		}
		ArtKit.plot(img, 4, 4, new String[] {
				"l..ll..l",
				"llllllll",
				"ll.ll.ll",
				"llllllll",
				"lll..lll",
				"lll..lll"
		});
		// brass pins, the same gold as the bell
		img.setRGB(3, 3, ArtKit.GOLD_HI);
		img.setRGB(12, 3, ArtKit.GOLD);
		img.setRGB(3, 11, ArtKit.GOLD_MID);
		img.setRGB(12, 11, ArtKit.GOLD_DK);
		// a ruler along the near edge
		for (int x = 1; x <= 14; x++) img.setRGB(x, 14, x % 2 == 0 ? ArtKit.GOLD : ArtKit.GOLD_DK);
		return img;
	}

	private static BufferedImage tableSide() {
		BufferedImage img = ArtKit.planks();
		// a drawer
		for (int x = 3; x <= 12; x++) {
			img.setRGB(x, 5, ArtKit.OAK_SH);
			img.setRGB(x, 11, ArtKit.OAK_SH);
		}
		for (int y = 5; y <= 11; y++) {
			img.setRGB(3, y, ArtKit.OAK_SH);
			img.setRGB(12, y, ArtKit.OAK_SH);
		}
		for (int x = 6; x <= 9; x++) img.setRGB(x, 8, ArtKit.GOLD_MID);
		ArtKit.bevel(img);
		return img;
	}

	private static BufferedImage tableFront() {
		BufferedImage img = ArtKit.planks();
		// hanging hammer
		ArtKit.plot(img, 2, 2, new String[] {
				".iiiiii.",
				".iIIIIi.",
				"...CC...",
				"...CC...",
				"...CC...",
				"...CC...",
				"...aa...",
				"........",
				"........",
				"........"
		});
		// a rolled blueprint
		ArtKit.plot(img, 10, 3, new String[] {
				".bbbb.",
				"oblllbo",
				"oblllbo",
				"oblllbo",
				".bobo.",
				"..g.."
		});
		ArtKit.bevel(img);
		return img;
	}

	private static BufferedImage stallFront() {
		BufferedImage img = ArtKit.planks();
		// rows 0-3 are outside the model's front UV; keep them as the wood rail the awning hangs from
		for (int y = 0; y < 4; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, y == 0 ? ArtKit.OAK_HI : ArtKit.OAK_DK);
		// crate of apples and a crate of emeralds, which is what the player actually sees
		ArtKit.plot(img, 1, 5, new String[] {
				"oCCCCCCo",
				"oCRrRrCo",
				"oCrRrRCo",
				"oCRrRrCo",
				"oCCCCCCo",
				"oCCCCCCo",
				"oCyyyyCo",
				"oCCCCCCo",
				"oooooooo"
		});
		ArtKit.plot(img, 9, 6, new String[] {
				".oeeeo.",
				"oeEEEEo",
				"oeEddEo",
				".oeEeo.",
				"..oeo..",
				".oGGGo.",
				"oGGGGGo"
		});
		return img;
	}

	private static BufferedImage stallSide() {
		BufferedImage img = ArtKit.planks();
		for (int y = 0; y < 4; y++) for (int x = 0; x < 16; x++) img.setRGB(x, y, ArtKit.OAK_DK);
		for (int y = 4; y < 16; y++) {
			img.setRGB(2, y, ArtKit.OAK_SH);
			img.setRGB(13, y, ArtKit.OAK_SH);
		}
		return img;
	}

	private static BufferedImage awning() {
		BufferedImage img = ArtKit.image(16, 16);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				boolean red = (x / 2) % 2 == 0;
				int c = red ? ArtKit.HEART : ArtKit.CREAM;
				if (x % 2 == 0) c = ArtKit.lighten(c, 1.12);
				else c = ArtKit.darken(c, 0.86);
				if (y == 15) c = ArtKit.darken(c, 0.7);
				img.setRGB(x, y, c);
			}
		}
		return img;
	}

	/** The Blank Marker: an oak plaque with an empty recess and four brass nails, ready for a glyph. */
	public static BufferedImage blankMarker() {
		return ArtKit.icon16(
				"................",
				".oooooooooooooo.",
				".occcccccccccco.",
				".ocgCCCCCCCCgco.",
				".ocCooooooooCco.",
				".ocCoAAAAAAoCco.",
				".ocCoAAAAAAoCco.",
				".ocCoAAAAAAoCco.",
				".ocCoAAAAAAoCco.",
				".ocCoAAAAAAoCco.",
				".ocCoAAAAAAoCco.",
				".ocCooooooooCco.",
				".ocgCCCCCCCCgco.",
				".occcccccccccco.",
				".oooooooooooooo.",
				"................");
	}
}
