package dev.villageheart.worker.job;

import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** The words for a worker's status (the Heart screen shows them). Fallbacks make the text readable on a server without the lang file. */
public final class WorkerStatusText {
	private WorkerStatusText() {}

	public static String key(WorkerStatus status, JobType job) {
		return switch (status) {
			case NEEDS_TOOL -> "status.villageheart.needs_tool." + job.tool().map(t -> t.kind().id()).orElse("tool");
			case NO_WORK -> "status.villageheart.no_work." + (job == JobType.LUMBERJACK ? "lumberjack" : "generic");
			default -> "status.villageheart." + status.name().toLowerCase(Locale.ROOT);
		};
	}

	public static Component of(WorkerStatus status, JobType job) {
		return Component.translatableWithFallback(key(status, job), fallback(status, job));
	}

	static String fallback(WorkerStatus status, JobType job) {
		return switch (status) {
			case OK -> "Working";
			case WORKPLACE_INVALID -> "Workplace invalid";
			case STORAGE_FULL -> "Storage full";
			case STORAGE_UNREACHABLE -> "Storage unreachable";
			case NEEDS_TOOL -> job.tool().map(t -> switch (t.kind()) {
				case AXE -> "Needs axe";
				case PICKAXE -> "Needs pickaxe";
				case HOE -> "Needs hoe";
				case BOW_OR_SWORD -> "Needs bow or sword";
			}).orElse("Needs tool");
			case NO_WORK -> job == JobType.LUMBERJACK ? "No trees within 32 blocks" : "Nothing to do";
		};
	}
}
