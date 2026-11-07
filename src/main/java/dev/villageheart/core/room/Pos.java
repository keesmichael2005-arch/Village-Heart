package dev.villageheart.core.room;

/** A block position. {@code toString()} is "(x, y, z)", the format every reason uses. Order: x, then y, then z. */
public record Pos(int x, int y, int z) implements Comparable<Pos> {
	private static final int XZ_BITS = 26, Y_BITS = 12;
	private static final long XZ_MASK = (1L << XZ_BITS) - 1, Y_MASK = (1L << Y_BITS) - 1;

	public Pos offset(Dir d) { return new Pos(x + d.dx, y + d.dy, z + d.dz); }
	public Pos above() { return new Pos(x, y + 1, z); }
	public Pos below() { return new Pos(x, y - 1, z); }

	/** Same layout as BlockPos.asLong: x in bits 38..63, z in 12..37, y in 0..11. */
	public long pack() { return pack(x, y, z); }

	public static long pack(int x, int y, int z) {
		return ((x & XZ_MASK) << (XZ_BITS + Y_BITS)) | ((z & XZ_MASK) << Y_BITS) | (y & Y_MASK);
	}

	public static Pos unpack(long v) {
		return new Pos((int) (v >> (XZ_BITS + Y_BITS)), (int) (v << (64 - Y_BITS) >> (64 - Y_BITS)),
				(int) (v << (64 - XZ_BITS - Y_BITS) >> (64 - XZ_BITS)));
	}

	@Override
	public int compareTo(Pos o) {
		int c = Integer.compare(x, o.x);
		if (c != 0) return c;
		c = Integer.compare(y, o.y);
		return c != 0 ? c : Integer.compare(z, o.z);
	}

	@Override
	public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
