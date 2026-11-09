package dev.villageheart.core.blueprint;

import dev.villageheart.core.HeartLocation;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * A build in progress, saved with the village. The steps are recomputed from (blueprint, params, ground, rotation), so only the cursor is
 * stored. {@code carried} is what the {@code carrier} (a builder) took from Storage for this build and still holds, by item id.
 * The reservations are not saved (M6: transient); the build system works them out again.
 */
public record BuildJob(UUID id, String blueprintId, BlueprintParams params, HeartLocation ground, Rotation4 rotation, long createdAt,
		UUID startedBy, int cursor, Map<String, Integer> carried, @Nullable UUID carrier) {
	public BuildJob {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(blueprintId, "blueprintId");
		Objects.requireNonNull(params, "params");
		Objects.requireNonNull(ground, "ground");
		Objects.requireNonNull(rotation, "rotation");
		Objects.requireNonNull(startedBy, "startedBy");
		Objects.requireNonNull(carried, "carried");
		if (cursor < 0) throw new IllegalArgumentException("cursor must be >= 0: " + cursor);
		TreeMap<String, Integer> clean = new TreeMap<>();
		for (Map.Entry<String, Integer> e : carried.entrySet()) {
			if (e.getValue() < 0) throw new IllegalArgumentException("carried must be >= 0: " + e);
			if (e.getValue() > 0) clean.put(e.getKey(), e.getValue());
		}
		carried = java.util.Collections.unmodifiableMap(clean);
	}

	public static BuildJob start(UUID id, String blueprintId, HeartLocation ground, Rotation4 rotation, long now, UUID startedBy) {
		return start(id, blueprintId, BlueprintParams.NONE, ground, rotation, now, startedBy);
	}

	public static BuildJob start(UUID id, String blueprintId, BlueprintParams params, HeartLocation ground, Rotation4 rotation, long now,
			UUID startedBy) {
		return new BuildJob(id, blueprintId, params, ground, rotation, now, startedBy, 0, Map.of(), null);
	}

	public BuildJob withCursor(int c) {
		return new BuildJob(id, blueprintId, params, ground, rotation, createdAt, startedBy, c, carried, carrier);
	}

	public BuildJob withCarried(Map<String, Integer> c) {
		return new BuildJob(id, blueprintId, params, ground, rotation, createdAt, startedBy, cursor, c, carrier);
	}

	public BuildJob withCarrier(@Nullable UUID c) {
		return new BuildJob(id, blueprintId, params, ground, rotation, createdAt, startedBy, cursor, carried, c);
	}

	public int carriedOf(String itemId) { return carried.getOrDefault(itemId, 0); }

	public Placement placement() { return new Placement(new dev.villageheart.core.room.Pos(ground.x(), ground.y(), ground.z()), rotation); }
}
