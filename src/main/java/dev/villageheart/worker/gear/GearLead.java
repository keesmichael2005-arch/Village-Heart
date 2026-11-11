package dev.villageheart.worker.gear;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.gear.CampTool;
import dev.villageheart.core.smith.SmithRecipes;
import dev.villageheart.core.worker.JobType;
import java.util.function.ToIntFunction;
import net.minecraft.server.MinecraftServer;

/**
 * The lead time of the worn line for one camp: how long the old tool must still last after a replacement is ordered. 0 for a self-craft
 * (the worker does not use its tool while it makes the next one). M19 returns the smith's {@code craftTicks + 600} here while a smith
 * serves; every census call site goes through this one place.
 */
public final class GearLead {
	private GearLead() {}

	public static int forCamp(MinecraftServer server, VillageState village, HeartLocation building, JobType job) { return 0; }

	/**
	 * Lead of each worn tool while a smith serves: that tool's replacement time plus the delivery allowance. Zero otherwise, so a
	 * village with no smith keeps the self-craft line.
	 */
	public static ToIntFunction<CampTool> lead(MinecraftServer server, VillageState village) {
		if (!SmithService.servesNow(server, village)) return tool -> 0;
		return tool -> SmithRecipes.replacementLead(tool.tier(), true);
	}
}
