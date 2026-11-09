package dev.villageheart.core.heart;

import java.util.Objects;
import java.util.UUID;

/**
 * @param jobId       "" for none
 * @param xpForNext   0 at the top level, or when there is no job
 * @param levelCapped the worker has the XP for its next level but the village tier holds it back (M15: the bar turns gold)
 */
public record VillagerRow(UUID id, String name, String jobId, String vanillaProfession, int level, int xp, int xpForNext,
		NeedsFlags needs, ActivityKind activity, boolean workplaceInvalid, boolean nitwit, boolean hasTraded, boolean loaded,
		boolean levelCapped, GuardShiftView shift) {
	/** A row of a villager that is no guard (the shape before M18). */
	public VillagerRow(UUID id, String name, String jobId, String vanillaProfession, int level, int xp, int xpForNext,
			NeedsFlags needs, ActivityKind activity, boolean workplaceInvalid, boolean nitwit, boolean hasTraded, boolean loaded,
			boolean levelCapped) {
		this(id, name, jobId, vanillaProfession, level, xp, xpForNext, needs, activity, workplaceInvalid, nitwit, hasTraded, loaded, levelCapped, GuardShiftView.NONE);
	}

	/** A row that is not level-capped. */
	public VillagerRow(UUID id, String name, String jobId, String vanillaProfession, int level, int xp, int xpForNext,
			NeedsFlags needs, ActivityKind activity, boolean workplaceInvalid, boolean nitwit, boolean hasTraded, boolean loaded) {
		this(id, name, jobId, vanillaProfession, level, xp, xpForNext, needs, activity, workplaceInvalid, nitwit, hasTraded, loaded, false);
	}

	public VillagerRow {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(vanillaProfession, "vanillaProfession");
		Objects.requireNonNull(needs, "needs");
		Objects.requireNonNull(activity, "activity");
		Objects.requireNonNull(shift, "shift");
	}

	public boolean isWorker() { return !jobId.isEmpty(); }
}
