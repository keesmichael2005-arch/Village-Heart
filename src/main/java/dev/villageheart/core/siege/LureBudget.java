package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Which mobs may path toward the village (docs/plans/M25.md 3.11). At most {@link Balance#MAX_LURED}.
 * Siege zombies take a slot before a nearer natural zombie. A mob that is gone frees its slot.
 */
public final class LureBudget {
	private LureBudget() {}

	public record Mob(UUID id, boolean siege, double distance, boolean present) {}

	public static List<UUID> admit(List<Mob> mobs) {
		List<Mob> present = new ArrayList<>();
		for (Mob m : mobs) if (m.present()) present.add(m);
		present.sort(Comparator.comparing((Mob m) -> !m.siege()).thenComparingDouble(Mob::distance).thenComparing(Mob::id));
		List<UUID> out = new ArrayList<>();
		for (int i = 0; i < present.size() && i < Balance.MAX_LURED; i++) out.add(present.get(i).id());
		return List.copyOf(out);
	}
}
