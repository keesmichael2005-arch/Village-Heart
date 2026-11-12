package dev.villageheart.worker.smith;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.gear.SmithService;
import dev.villageheart.worker.gear.ToolCensus;
import net.minecraft.server.MinecraftServer;

/** A hired blacksmith at a registered, valid Smithy. While this is true, workers stop starting their own tools. */
public final class SmithServiceImpl implements SmithService {
	public static final SmithServiceImpl INSTANCE = new SmithServiceImpl();

	private SmithServiceImpl() {}

	@Override
	public boolean serves(MinecraftServer server, VillageState village) {
		for (WorkerRecord w : village.workers()) {
			if (!w.jobId().equals(JobType.BLACKSMITH.id())) continue;
			if (ToolCensus.valid(server, w.building())) return true;
		}
		return false;
	}
}
