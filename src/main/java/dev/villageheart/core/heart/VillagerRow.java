package dev.villageheart.core.heart;

import java.util.Objects;
import java.util.UUID;

/**
 * @param jobId     "" for none
 * @param xpForNext 0 at the top level, or when there is no job
 */
public record VillagerRow(UUID id, String name, String jobId, String vanillaProfession, int level, int xp, int xpForNext,
		NeedsFlags needs, ActivityKind activity, boolean workplaceInvalid, boolean nitwit, boolean hasTraded, boolean loaded) {
	public VillagerRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(vanillaProfession, "vanillaProfession");
		Objects.requireNonNull(needs, "needs");
		Objects.requireNonNull(activity, "activity");
	}

	public boolean isWorker() { return !jobId.isEmpty(); }
}
