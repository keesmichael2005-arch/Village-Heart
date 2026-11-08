package dev.villageheart.needs;

import dev.villageheart.core.WorkerRecord;
import dev.villageheart.world.VillageClaims;
import net.minecraft.server.MinecraftServer;

/** Whether a worker is paid (M12): everyone who is not on today's unpaid list. With wages off (tests) the list stays empty. */
public final class PaidSource {
	private PaidSource() {}

	public static boolean paid(MinecraftServer server, WorkerRecord rec) {
		return !dev.villageheart.economy.Wages.onStrike(server, rec.villager());
	}
}
