package dev.villageheart.growth;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.growth.License;
import dev.villageheart.world.VillageSystem;
import net.minecraft.server.MinecraftServer;

/** Every tick: keeps the breeding gate current and walks today's licence through its courtship (M23 plan 3.4). */
public final class GrowthSystem implements VillageSystem {
	public static final GrowthSystem INSTANCE = new GrowthSystem();

	private GrowthSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		Growth.refreshGate(server);
		License lic = Growth.state(server).licence().filter(License::active).orElse(null);
		if (lic == null) {
			Courtship.forget();
			return;
		}
		Courtship.step(server, village, lic);
	}
}
