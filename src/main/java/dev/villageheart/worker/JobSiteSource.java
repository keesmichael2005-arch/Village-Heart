package dev.villageheart.worker;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.SiteView;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/**
 * M7's one narrow seam to the building registry (M4/M5). {@link #DEFAULT} ({@link BuildingJobSites}) is the only class that touches
 * building types; GameTests swap in a fixed source with {@code Workers.setJobSiteSourceForTest}.
 */
public interface JobSiteSource {
	/** Every VALID building that offers {@code job}: ALL its job blocks of that job (position order) and its slot count. */
	List<SiteView> sites(MinecraftServer server, JobType job);

	/** True while a building record exists for this marker (any status). Invalid is not removed. */
	boolean isRegistered(MinecraftServer server, HeartLocation building);

	/** Every block in a VALID building that is the job block of ANY job (all of them, slots or not). */
	List<HeartLocation> reservable(MinecraftServer server);

	/**
	 * True while some valid building's contents are unknown (no cached scan, right after a restart): {@link #reservable} is then
	 * incomplete, so the reservation pass must not release anything.
	 */
	boolean contentsPending(MinecraftServer server);

	JobSiteSource DEFAULT = new BuildingJobSites();
}
