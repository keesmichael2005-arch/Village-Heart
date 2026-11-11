package dev.villageheart.core.siege;

import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which defence cells a mob opens (docs/plans/M25.md 3.11). The opening is one block toward the goal and covers the body:
 * a zombie or a vindicator is 1 wide and 2 high, a ravager is 2 wide and 3 high. The second column is the positive
 * perpendicular ( +x when the mob faces along z, +z when it faces along x ). Cells behind the mob, and cells more than
 * 2 blocks away (Chebyshev), are ignored. Nearest wins; a gate wins a tie, then the lower cell, then x, then z.
 */
public final class BreachChoice {
	private BreachChoice() {}

	public static List<DefenceCell> pick(int mobX, int mobY, int mobZ, int wide, int tall, int goalX, int goalY, int goalZ, List<DefenceCell> cells) {
		int dx = goalX - mobX, dz = goalZ - mobZ;
		if (dx == 0 && dz == 0) return List.of();
		boolean alongX = Math.abs(dx) >= Math.abs(dz);
		int sign = Integer.signum(alongX ? dx : dz);
		List<DefenceCell> out = new ArrayList<>();
		for (DefenceCell c : cells) {
			Pos p = c.pos();
			int cx = p.x() - mobX, cy = p.y() - mobY, cz = p.z() - mobZ;
			if (Math.max(Math.abs(cx), Math.max(Math.abs(cy), Math.abs(cz))) > 2) continue;
			if (cx * dx + cz * dz < 0) continue;
			int along = (alongX ? cx : cz) * sign;
			int side = alongX ? cz : cx;
			if (along != 1 || cy < 0 || cy >= tall || side < 0 || side >= wide) continue;
			out.add(c);
		}
		out.sort(Comparator.comparingLong((DefenceCell c) -> dist2(c, mobX, mobY, mobZ))
				.thenComparingInt(c -> c.kind() == DefenceKind.GATE ? 0 : 1)
				.thenComparingInt(c -> c.pos().y())
				.thenComparingInt(c -> c.pos().x())
				.thenComparingInt(c -> c.pos().z()));
		return List.copyOf(out);
	}

	private static long dist2(DefenceCell c, int mobX, int mobY, int mobZ) {
		long dx = c.pos().x() - mobX, dy = c.pos().y() - mobY, dz = c.pos().z() - mobZ;
		return dx * dx + dy * dy + dz * dz;
	}
}
