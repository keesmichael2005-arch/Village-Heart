package dev.villageheart.core.defence;

import dev.villageheart.core.blueprint.DefenceBlueprints;
import java.util.List;
import java.util.Optional;

/**
 * What a recorded defence is made of and where guards stand next to it (docs/plans/M21.md 3.2 #8), in the blueprint's local frame
 * ({@code y} is the layer, 0 = feet level of someone standing on the ground). The world cells come from {@code Placement.world}.
 */
public final class DefenceSpecs {
	private DefenceSpecs() {}

	/** A block that must still be there for the site to count. */
	public record KeyCell(int x, int y, int z, String blockId) {}

	/** @param patrolCandidates local (x, z) feet cells, best first: the first one a person can stand in is the patrol point */
	public record Spec(List<int[]> patrolCandidates, List<KeyCell> keys) {}

	private static final Spec LAMP = new Spec(List.of(new int[] {0, -1}, new int[] {0, 1}, new int[] {-1, 0}, new int[] {1, 0}),
			List.of(new KeyCell(0, 2, 0, "minecraft:lantern")));

	private static final Spec TOWER = new Spec(List.of(new int[] {2, 0}, new int[] {2, 4}, new int[] {0, 2}, new int[] {4, 2}),
			List.of(new KeyCell(2, 0, 2, "minecraft:ladder"), new KeyCell(2, 5, 2, "minecraft:ladder")));

	/** Walls and gates have none: no patrol point, nothing reads them. */
	public static Optional<Spec> of(String blueprintId) {
		return switch (blueprintId) {
			case DefenceBlueprints.LAMP_POST_ID -> Optional.of(LAMP);
			case DefenceBlueprints.WATCHTOWER_ID -> Optional.of(TOWER);
			default -> Optional.empty();
		};
	}
}
