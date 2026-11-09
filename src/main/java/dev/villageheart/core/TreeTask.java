package dev.villageheart.core;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Objects;

/**
 * A tree the lumberjacks of one camp are working on, saved with the village so a fell survives panic, night, reload, firing and death:
 * any lumberjack of that camp resumes it. FELLING: the logs are being removed; {@code base} are the soil-resting logs, {@code box} the
 * extent the tree had when the fell started (a resume never floods outside it). REPLANT: {@code base} are the cells still to plant.
 */
public record TreeTask(HeartLocation building, Stage stage, List<HeartLocation> base, String sapling, long since, Pos boxMin, Pos boxMax) {
	public enum Stage { FELLING, REPLANT }

	public TreeTask {
		Objects.requireNonNull(building, "building");
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(base, "base");
		Objects.requireNonNull(sapling, "sapling");
		Objects.requireNonNull(boxMin, "boxMin");
		Objects.requireNonNull(boxMax, "boxMax");
		if (base.isEmpty()) throw new IllegalArgumentException("a tree task needs at least one base cell");
		base = List.copyOf(base);
	}

	/** The key of the task: its first base cell. */
	public HeartLocation key() { return base.get(0); }

	public boolean sharesCellWith(TreeTask o) {
		for (HeartLocation b : base) if (o.base.contains(b)) return true;
		return false;
	}

	public TreeTask withSince(long newSince) {
		return new TreeTask(building, stage, base, sapling, newSince, boxMin, boxMax);
	}

	public TreeTask withStage(Stage s, List<HeartLocation> newBase) {
		return new TreeTask(building, s, newBase, sapling, since, boxMin, boxMax);
	}
}
