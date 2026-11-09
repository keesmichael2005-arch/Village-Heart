package dev.villageheart.core.sim;

/**
 * The ground heights of the ramp ring around the flattened {@code site} patch (harness only). Pure: the natural ground is a function from a
 * column to its height.
 * <p>
 * The result is a height field on the patch plus a margin around it where
 * <ul>
 * <li>the patch is exactly {@code floor};</li>
 * <li>a column k blocks outside the patch (Chebyshev) is within k of the floor, so the patch may differ from the land by up to k;</li>
 * <li>every two columns that share an edge differ by at most 1, so a villager can walk up or down every step, in any direction;</li>
 * <li>no column is higher than the natural ground (clamped to the cone above): the field is the highest one-block-per-step slope under the land, so
 * hills are cut and pits are filled to the slope and nothing is built up into the air.</li>
 * </ul>
 * The margin starts at {@code ramp + 3} and grows (to {@code ramp + 12}) while the outermost layer still differs from the land by more than 1,
 * so the ring meets the land. Beyond the last layer the land is untouched, and its own roughness is its own.
 */
public final class RampProfile {
	private RampProfile() {}

	/** Marks a column that is not loaded: it is left out of the neighbour rules. */
	public static final int UNKNOWN = Integer.MIN_VALUE;

	@FunctionalInterface
	public interface Ground {
		/** @return the ground height of the column (patch-relative x, z), or {@link #UNKNOWN} */
		int height(int x, int z);
	}

	/**
	 * @param margin  columns around the patch that are covered
	 * @param h       {@code h[x + margin][z + margin]} for x in -margin..w+margin-1, z in -margin..d+margin-1 (the patch is 0..w-1, 0..d-1); {@link #UNKNOWN} stays unknown
	 * @param meets   the outermost layer is within 1 of the land
	 */
	public record Ramp(int margin, int[][] h, boolean meets) {}

	public static Ramp solve(Ground natural, int w, int d, int floor, int ramp) {
		Ramp r = null;
		for (int margin = ramp + 3; margin <= ramp + 12; margin += 3) {
			r = solveWithMargin(natural, w, d, floor, margin);
			if (r.meets()) break;
		}
		return r;
	}

	/** One attempt with a fixed margin. */
	public static Ramp solveWithMargin(Ground natural, int w, int d, int floor, int margin) {
		int nx = w + 2 * margin, nz = d + 2 * margin;
		int[][] c = new int[nx][nz], u = new int[nx][nz];
		for (int i = 0; i < nx; i++) for (int j = 0; j < nz; j++) {
			int x = i - margin, z = j - margin;
			int k = Math.max(Math.max(-x, x - (w - 1)), Math.max(-z, z - (d - 1)));
			int e = natural.height(x, z);
			c[i][j] = k <= 0 ? floor : e == UNKNOWN ? UNKNOWN : Math.max(floor - k, Math.min(floor + k, e));
			u[i][j] = c[i][j] == UNKNOWN ? UNKNOWN : c[i][j];
		}
		// u(p) = min over q of c(q) + |p - q|: the highest field that is at most c and rises by at most 1 per step (two chamfer passes)
		for (int i = 0; i < nx; i++) for (int j = 0; j < nz; j++) {
			if (u[i][j] == UNKNOWN) continue;
			if (i > 0 && u[i - 1][j] != UNKNOWN) u[i][j] = Math.min(u[i][j], u[i - 1][j] + 1);
			if (j > 0 && u[i][j - 1] != UNKNOWN) u[i][j] = Math.min(u[i][j], u[i][j - 1] + 1);
		}
		for (int i = nx - 1; i >= 0; i--) for (int j = nz - 1; j >= 0; j--) {
			if (u[i][j] == UNKNOWN) continue;
			if (i < nx - 1 && u[i + 1][j] != UNKNOWN) u[i][j] = Math.min(u[i][j], u[i + 1][j] + 1);
			if (j < nz - 1 && u[i][j + 1] != UNKNOWN) u[i][j] = Math.min(u[i][j], u[i][j + 1] + 1);
		}
		boolean meets = true;
		for (int i = 0; i < nx; i++) for (int j = 0; j < nz; j++) {
			boolean frame = i == 0 || j == 0 || i == nx - 1 || j == nz - 1;
			if (frame && u[i][j] != UNKNOWN && Math.abs(u[i][j] - natural.height(i - margin, j - margin)) > 1) meets = false;
		}
		return new Ramp(margin, u, meets);
	}
}
