package dev.villageheart.worker;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.SiteView;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * The real {@link JobSiteSource}: reads the M5 building registry. A valid building with no cached scan is "contents unknown" and is
 * skipped by {@link #sites} and {@link #reservable} (and reported by {@link #contentsPending}). {@code Verdict.slots} is used as the
 * slot count of the building's own job (it counts job blocks, or min(job blocks, beds) for Barracks); for Storage it is the container
 * count, which is irrelevant here because no job is offered by Storage.
 */
final class BuildingJobSites implements JobSiteSource {
	private static HeartLocation at(String dimension, Pos p) { return new HeartLocation(dimension, p.x(), p.y(), p.z()); }

	@Override
	public List<SiteView> sites(MinecraftServer server, JobType job) {
		List<SiteView> out = new ArrayList<>();
		for (Buildings.BuildingView b : Buildings.valid(server, job.building())) {
			if (b.latestScan().isEmpty()) continue;
			String dim = b.level().dimension().identifier().toString();
			List<HeartLocation> blocks = new ArrayList<>();
			for (Pos p : b.latestScan().get().positions(job.jobBlockKey())) blocks.add(at(dim, p));
			int slots = b.verdict().map(Verdict::slots).orElse(blocks.size());
			out.add(new SiteView(VillageClaims.locate(b.level(), b.marker()), blocks, slots));
		}
		return out;
	}

	@Override
	public boolean isRegistered(MinecraftServer server, HeartLocation building) {
		return VillageClaims.current(server).flatMap(v -> v.building(building)).isPresent();
	}

	@Override
	public List<HeartLocation> reservable(MinecraftServer server) {
		Set<String> keys = new LinkedHashSet<>();
		for (JobType j : JobType.values()) keys.add(j.jobBlockKey());
		Set<HeartLocation> out = new LinkedHashSet<>();
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.status() != dev.villageheart.core.building.BuildingStatus.VALID || b.latestScan().isEmpty()) continue;
			RoomScan scan = b.latestScan().get();
			String dim = b.level().dimension().identifier().toString();
			for (String key : keys) for (Pos p : scan.positions(key)) out.add(at(dim, p));
		}
		return new ArrayList<>(out);
	}

	@Override
	public boolean contentsPending(MinecraftServer server) {
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.status() == dev.villageheart.core.building.BuildingStatus.VALID && b.latestScan().isEmpty()) return true;
		}
		return false;
	}
}
