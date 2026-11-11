package dev.villageheart.core.siege;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.DayPhase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The next wall repair (docs/plans/M25.md 3.15). Nothing during a threat or outside {@link DayPhase#WORK}, and an opened
 * cell is never planned. Order: breached gate, breached stone, a crack at or above half its hit points, then a lighter
 * crack. Inside a class, nearest the Heart, then lower y, then x, then z. A breach costs one of its own material.
 * A heavy crack costs one cobblestone. A light crack is free. Short of material, the builder waits and names the item.
 */
public final class RepairPlanner {
	public static final String COBBLESTONE = "minecraft:cobblestone";
	public static final String FENCE_GATE = "#minecraft:fence_gates";

	private RepairPlanner() {}

	public record Cell(long packed, DefenceKind kind, int damage, boolean breached, boolean opened) {}

	public sealed interface Plan permits Repair, Wait {}

	public record Repair(long packed, DefenceKind kind, int cost, String materialId) implements Plan {}

	public record Wait(String item, int missing) implements Plan {}

	public static Optional<Plan> next(boolean underAttack, DayPhase phase, List<Cell> cells, HeartLocation heart, Map<String, Integer> stock) {
		if (underAttack || phase != DayPhase.WORK) return Optional.empty();
		List<Cell> work = new ArrayList<>();
		for (Cell c : cells) if (!c.opened() && rank(c) >= 0) work.add(c);
		if (work.isEmpty()) return Optional.empty();
		work.sort(order(heart));
		Cell best = work.get(0);
		int cost = cost(best);
		String material = material(best);
		if (cost > 0 && stock.getOrDefault(material, 0) < cost) return Optional.of(new Wait(itemName(material), cost - stock.getOrDefault(material, 0)));
		return Optional.of(new Repair(best.packed(), best.kind(), cost, material));
	}

	private static int rank(Cell c) {
		if (c.breached()) return c.kind() == DefenceKind.GATE ? 0 : 1;
		if (c.damage() <= 0) return -1;
		return c.damage() >= WallHp.hp(c.kind()) / 2 ? 2 : 3;
	}

	private static int cost(Cell c) {
		if (c.breached()) return 1;
		return c.damage() >= WallHp.hp(c.kind()) / 2 ? 1 : 0;
	}

	private static String material(Cell c) {
		if (c.breached() && c.kind() == DefenceKind.GATE) return FENCE_GATE;
		return COBBLESTONE;
	}

	private static String itemName(String material) { return material.equals(FENCE_GATE) ? "fence gate" : "cobblestone"; }

	private static Comparator<Cell> order(HeartLocation heart) {
		return Comparator.comparingInt(RepairPlanner::rank)
				.thenComparingLong(c -> dist2(c, heart))
				.thenComparingInt(c -> Pos.unpack(c.packed()).y())
				.thenComparingInt(c -> Pos.unpack(c.packed()).x())
				.thenComparingInt(c -> Pos.unpack(c.packed()).z());
	}

	private static long dist2(Cell c, HeartLocation heart) {
		Pos p = Pos.unpack(c.packed());
		long dx = p.x() - heart.x(), dy = p.y() - heart.y(), dz = p.z() - heart.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
