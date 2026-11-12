package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Flush, crack resend, the slow scan, and player-opened cells (docs/plans/M25.md 3.9, 3.10). */
public final class SiegeSystem implements VillageSystem {
	public static final SiegeSystem INSTANCE = new SiegeSystem();

	private SiegeSystem() {}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level) SiegeDamage.onPlayerBreak(level, pos);
		});
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		BreachNotices.observe(server);
		if (village.defences().isEmpty() && village.threats().damage().isEmpty()) return;
		try (var t = VillageTicker.section()) {
			long time = server.overworld().getGameTime();
			if (time % 20 == 0) SiegeDamage.scan(server, village, (int) Math.floorMod(time / 20, 10));
			if (time % Balance.DAMAGE_FLUSH_TICKS == 0) SiegeDamage.flush(server);
			if (time % Balance.CRACK_RESEND_TICKS == 0) SiegeCracks.resend(server, village);
		}
	}
}
