package dev.villageheart.stock;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageSystem;
import net.minecraft.server.MinecraftServer;

/** Refreshes the storage alert once a second. Registered after the building system; its cost is inside {@code village_ms}. */
public final class StockpileSystem implements VillageSystem {
	public static final StockpileSystem INSTANCE = new StockpileSystem();

	private StockpileSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.overworld().getGameTime() % Balance.STOCKPILE_REFRESH_TICKS != 0) return;
		Stockpiles.refreshAlert(server);
	}
}
