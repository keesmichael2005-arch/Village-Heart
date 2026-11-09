package dev.villageheart.worker.job;

import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import net.minecraft.network.chat.Component;

/**
 * The words for a worker's status. One set with the Heart screen: the keys are {@code gui.villageheart.heart.activity.*} (HeartTexts), with
 * the English text as fallback so a server without the lang file still reads well.
 */
public final class WorkerStatusText {
	private WorkerStatusText() {}

	public static ActivityKind activityOf(WorkerStatus status) {
		return switch (status) {
			case OK -> ActivityKind.WORKING;
			case WORKPLACE_INVALID -> ActivityKind.AWAY; // shown through its own key below
			case STORAGE_FULL -> ActivityKind.STORAGE_FULL;
			case STORAGE_UNREACHABLE -> ActivityKind.STORAGE_UNREACHABLE;
			case NEEDS_TOOL -> ActivityKind.NEEDS_TOOL;
			case NO_WORK -> ActivityKind.NO_WORK;
			case SITE_UNREACHABLE -> ActivityKind.SITE_UNREACHABLE;
			case NEEDS_MATERIALS -> ActivityKind.NEEDS_MATERIALS;
			case BLOCKED -> ActivityKind.BLOCKED;
			case TOO_HUNGRY -> ActivityKind.TOO_HUNGRY;
			case ON_STRIKE -> ActivityKind.ON_STRIKE;
			case MAKING_WOODEN_TOOL -> ActivityKind.MAKING_WOODEN_TOOL;
			case MAKING_STONE_TOOL -> ActivityKind.MAKING_STONE_TOOL;
		};
	}

	public static String key(WorkerStatus status, JobType job) {
		if (status == WorkerStatus.WORKPLACE_INVALID) return HeartTexts.key("activity.workplace_invalid");
		return HeartTexts.activityKey(activityOf(status), job.id());
	}

	public static Component of(WorkerStatus status, JobType job) {
		String key = key(status, job);
		return Component.translatableWithFallback(key, HeartTexts.EN.get(key));
	}
}
