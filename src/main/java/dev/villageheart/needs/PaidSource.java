package dev.villageheart.needs;

import dev.villageheart.core.WorkerRecord;
import dev.villageheart.world.VillageClaims;
import net.minecraft.server.MinecraftServer;

/** Whether a worker is paid (M12): everyone who is not on today's unpaid list. With wages off (tests) the list stays empty. */
public final class PaidSource {
	private PaidSource() {}

	public static boolean paid(MinecraftServer server, WorkerRecord rec) {
		// M26-R: a merchant never strikes (core.economy.StrikeRules), so it counts as paid and keeps working while its wage is owed
		boolean mayStrike = dev.villageheart.core.worker.JobType.byId(rec.jobId()).map(dev.villageheart.core.economy.StrikeRules::mayStrike).orElse(true);
		return !mayStrike || !dev.villageheart.economy.Wages.onStrike(server, rec.villager());
	}
}
