package dev.villageheart.core.blueprint;

import dev.villageheart.core.blueprint.SiteRules.Code;
import dev.villageheart.core.blueprint.SiteRules.SiteProblem;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import java.util.List;

/**
 * The two-click line of a wall or gate (docs/plans/M21.md 3.2 #1): start and end must lie on one axis, at most {@code maxLength} blocks
 * long. The blueprint runs along local +x from the start column, so the rotation turns EAST into the direction start to end.
 */
public final class WallLines {
	private WallLines() {}

	/** @param ground the start block (the whole line is level at its y); @param length blocks, start and end column included */
	public record Line(Pos ground, Rotation4 rotation, int length) {}

	/** Either a line or the problem with it. */
	public record Result(Line line, SiteProblem problem) {
		public boolean ok() { return line != null; }
	}

	/** Check order: NOT_STRAIGHT, TOO_LONG, TOO_SHORT. */
	public static Result of(Pos start, Pos end, int minLength, int maxLength) {
		int dx = end.x() - start.x(), dz = end.z() - start.z();
		if (dx != 0 && dz != 0) {
			return fail(Code.NOT_STRAIGHT, "A wall must run straight along x or z (%s off the line)", Integer.toString(Math.min(Math.abs(dx), Math.abs(dz))));
		}
		int length = Math.abs(dx) + Math.abs(dz) + 1;
		if (length > maxLength) return fail(Code.TOO_LONG, "Too long: %s blocks (max " + maxLength + ")", Integer.toString(length));
		if (length < minLength) return fail(Code.TOO_SHORT, "Too short: %s blocks (min %s)", Integer.toString(length), Integer.toString(minLength));
		return new Result(new Line(start, Rotation4.turning(Dir.EAST, direction(start, end)), length), null);
	}

	/** The world direction from start to end along the line; EAST for a one-block line. */
	public static Dir direction(Pos start, Pos end) {
		int dx = end.x() - start.x(), dz = end.z() - start.z();
		if (dx > 0) return Dir.EAST;
		if (dx < 0) return Dir.WEST;
		if (dz > 0) return Dir.SOUTH;
		if (dz < 0) return Dir.NORTH;
		return Dir.EAST;
	}

	private static Result fail(Code code, String fallback, String... args) {
		return new Result(null, new SiteProblem(code, fallback, List.of(args), null));
	}
}
