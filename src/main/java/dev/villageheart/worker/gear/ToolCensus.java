package dev.villageheart.worker.gear;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.gear.CampDemand;
import dev.villageheart.core.gear.CampTool;
import dev.villageheart.core.gear.ToolDemand;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.building.Buildings;
import dev.villageheart.worker.hunt.HuntGear;
import dev.villageheart.worker.job.CampContainers;
import dev.villageheart.worker.job.CampTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Reads a camp's chests into a {@link CampDemand} (M19a plan 3.4). M19's DemandCensus calls it for every tool building. */
public final class ToolCensus {
	private ToolCensus() {}

	/** The camp's hired workers of this job, in hiring order (the order of the village's worker list). */
	public static List<UUID> hired(VillageState village, HeartLocation building, JobType job) {
		List<UUID> out = new ArrayList<>();
		for (WorkerRecord w : village.workers()) if (w.building().equals(building) && w.jobId().equals(job.id())) out.add(w.villager());
		return out;
	}

	/** The tools of the kind in the camp containers, as {@link CampTool}s with their durability. */
	public static Optional<List<CampTool>> toolsOf(MinecraftServer server, HeartLocation building, JobType job) {
		ToolKind kind = job.tool().orElseThrow().kind();
		Optional<List<CampContainers.Chest>> chests = CampContainers.of(server, building);
		if (chests.isEmpty()) return Optional.empty();
		List<CampTool> tools = new ArrayList<>();
		for (int ci = 0; ci < chests.get().size(); ci++) {
			Container c = chests.get().get(ci).container();
			for (int si = 0; si < c.getContainerSize(); si++) {
				ItemStack s = c.getItem(si);
				if (!CampTools.isTool(kind, s)) continue;
				int rank = job == JobType.HUNTER ? HuntGear.rank(s) : CampTools.tierOf(s).ordinal();
				tools.add(new CampTool(ci * 1000 + si, CampTools.tierOf(s), rank, ToolStacks.remaining(s), ToolStacks.max(s)));
			}
		}
		return Optional.of(tools);
	}

	/**
	 * The demand of one camp: unknown when the building is not registered, not valid, or its chests cannot be read (no scan, unloaded).
	 * {@code crafting} are the workers with an open order (left out of the per-worker allocation).
	 */
	public static CampDemand of(MinecraftServer server, VillageState village, HeartLocation building, JobType job, Set<UUID> crafting, int leadTicks) {
		return of(server, village, building, job, crafting, tool -> leadTicks);
	}

	/** The same census with a lead that depends on the worn tool (the smith's craft time of that tier). */
	public static CampDemand of(MinecraftServer server, VillageState village, HeartLocation building, JobType job, Set<UUID> crafting,
			ToIntFunction<CampTool> leadTicks) {
		List<UUID> hired = hired(village, building, job);
		if (job.tool().isEmpty() || !valid(server, building)) return CampDemand.unknown(hired);
		Optional<List<CampTool>> tools = toolsOf(server, building, job);
		if (tools.isEmpty()) return CampDemand.unknown(hired);
		return ToolDemand.of(building, job, hired, crafting, tools.get(), leadTicks);
	}

	/** Registered and valid; false for an unregistered or invalid building. */
	public static boolean valid(MinecraftServer server, HeartLocation building) {
		return CampContainers.building(server, building).map(b -> b.status() == BuildingStatus.VALID).orElse(false);
	}

	/** Is the building registered in the village at all (valid or not)? */
	public static boolean registered(VillageState village, HeartLocation building) { return village.building(building).isPresent(); }
}
