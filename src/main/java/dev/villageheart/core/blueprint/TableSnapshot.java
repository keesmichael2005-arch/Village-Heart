package dev.villageheart.core.blueprint;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.BuildRow;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything one viewer's Builder's Table screen shows (docs/plans/M20.md 3.7): the blueprints with their lock state and material list
 * ("have" is what a new build could still reserve), and the builds in progress. Records and lists only, so {@code equals} is the refresh dedupe.
 */
public record TableSnapshot(Rank viewerRank, Tier tier, List<TableBlueprint> blueprints, List<TableBuild> builds) {
	/** One page of the catalogue. The screen scrolls; the wire cap is high enough for the vanilla houses plus saved blueprints. */
	public static final int MAX_BLUEPRINTS = 160;
	public static final int MAX_MATERIALS = 20;

	public TableSnapshot {
		Objects.requireNonNull(viewerRank, "viewerRank");
		Objects.requireNonNull(tier, "tier");
		blueprints = List.copyOf(blueprints);
		builds = List.copyOf(builds);
	}

	/** @param locked the village's tier is below the blueprint's */
	public record TableBlueprint(String id, boolean locked, int width, int height, int depth, List<TableMaterial> materials) {
		public TableBlueprint {
			Objects.requireNonNull(id, "id");
			materials = List.copyOf(materials);
		}
	}

	/** @param name the material in words ("planks"); @param needed what the blueprint costs; @param have what Storage could still give a new build */
	public record TableMaterial(String name, int needed, long have) {
		public TableMaterial {
			Objects.requireNonNull(name, "name");
		}
	}

	public record TableBuild(UUID id, BuildRow row) {
		public TableBuild {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(row, "row");
		}
	}
}
