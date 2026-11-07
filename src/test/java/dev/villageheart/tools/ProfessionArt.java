package dev.villageheart.tools;

import dev.villageheart.core.worker.JobType;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Villager profession overlays (64x64, the vanilla villager UV layout), one per {@link JobType}: a coloured apron over the
 * whole robe (the 8x20x6 jacket cube at u=0, v=38), a sash and a 5x5 glyph on the chest. Everything else stays transparent, so the
 * vanilla head, nose and arms show through.
 */
public final class ProfessionArt {
	private ProfessionArt() {}

	public record Art(int apron, int sash, int glyph, String[] bitmap) {}

	/** Jacket cube: u=0, v=38, 8 wide, 20 tall, 6 deep. Front face is x 6..13, y 44..63. */
	static final int JACKET_U = 0, JACKET_V = 38, W = 8, H = 20, D = 6;
	static final int GLYPH_X = 6 + 1, GLYPH_Y = 44 + 12;
	static final int SASH_Y = 44 + 9; // just below the crossed arms, which hide the upper chest

	public static final Map<JobType, Art> ART = new EnumMap<>(JobType.class);

	static {
		ART.put(JobType.FARMER, new Art(0xFF4A7F2A, 0xFFE3C14B, 0xFFFFF1A0, new String[] {
				"#.#.#", ".#.#.", "..#..", ".###.", "..#.."}));
		ART.put(JobType.LUMBERJACK, new Art(0xFFA8322A, 0xFF3A2A1A, 0xFFEEDDCC, new String[] {
				".##..", "####.", ".###.", "...#.", "....#"}));
		ART.put(JobType.COOK, new Art(0xFFEDEDED, 0xFF9A9A9A, 0xFFC63D2A, new String[] {
				"..#..", ".##..", ".###.", "#####", ".###."}));
		ART.put(JobType.BUILDER, new Art(0xFFDE8A1C, 0xFF3F3F3F, 0xFF2B2B2B, new String[] {
				"####.", ".####", "####.", ".####", "####."}));
		ART.put(JobType.MINER, new Art(0xFF666B75, 0xFFC9962E, 0xFFFFE08A, new String[] {
				".###.", "#.#.#", "..#..", "..#..", "..#.."}));
		ART.put(JobType.HUNTER, new Art(0xFF3C5A38, 0xFFB08850, 0xFFE9E0C0, new String[] {
				"..#..", "...#.", "#####", "...#.", "..#.."}));
		ART.put(JobType.GUARD, new Art(0xFF3D5A8C, 0xFFC8CDD4, 0xFFE8EEF8, new String[] {
				"#####", "#####", "#####", ".###.", "..#.."}));
		ART.put(JobType.MERCHANT, new Art(0xFF6A3D8C, 0xFFE3B341, 0xFFFFE28A, new String[] {
				".###.", "#####", "##.##", "#####", ".###."}));
		ART.put(JobType.BLACKSMITH, new Art(0xFF2E2E34, 0xFFB5651D, 0xFFC9CED6, new String[] {
				"#####", ".###.", "..#..", ".###.", "#####"}));
	}

	public static boolean[][] bitmap(JobType job) {
		String[] rows = ART.get(job).bitmap();
		boolean[][] b = new boolean[5][5];
		for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) b[y][x] = rows[y].charAt(x) == '#';
		return b;
	}

	private static void rect(BufferedImage img, int x0, int y0, int x1, int y1, int argb) {
		for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) img.setRGB(x, y, argb);
	}

	/** Shade a colour (factor < 1 darker), keeping alpha opaque. */
	private static int shade(int argb, double f) {
		int r = (int) (((argb >> 16) & 0xFF) * f), g = (int) (((argb >> 8) & 0xFF) * f), b = (int) ((argb & 0xFF) * f);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	public static BufferedImage render(JobType job) {
		Art art = ART.get(job);
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		int u = JACKET_U, v = JACKET_V;
		// faces of the jacket cube: top, bottom, west, front, east, back
		rect(img, u + D, v, u + D + W - 1, v + D - 1, shade(art.apron(), 0.9));
		rect(img, u + D + W, v, u + D + 2 * W - 1, v + D - 1, shade(art.apron(), 0.8));
		rect(img, u, v + D, u + D - 1, v + D + H - 1, shade(art.apron(), 0.85));
		rect(img, u + D, v + D, u + D + W - 1, v + D + H - 1, art.apron());
		rect(img, u + D + W, v + D, u + 2 * D + W - 1, v + D + H - 1, shade(art.apron(), 0.85));
		rect(img, u + 2 * D + W, v + D, u + 2 * D + 2 * W - 1, v + D + H - 1, shade(art.apron(), 0.75));
		// sash around the waist: two rows on every side face
		for (int dy = 0; dy < 2; dy++) {
			int y = SASH_Y + dy;
			rect(img, u, y, u + 2 * D + 2 * W - 1, y, dy == 0 ? art.sash() : shade(art.sash(), 0.8));
		}
		// glyph on the chest
		boolean[][] g = bitmap(job);
		for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) if (g[y][x]) img.setRGB(GLYPH_X + x, GLYPH_Y + y, art.glyph());
		return img;
	}
}
