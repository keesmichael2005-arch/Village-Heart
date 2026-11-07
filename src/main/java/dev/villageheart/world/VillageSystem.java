package dev.villageheart.world;

import dev.villageheart.core.VillageState;
import net.minecraft.server.MinecraftServer;

/** A village-wide per-tick system (validation, needs, wages ...). Runs once per server tick while a claim exists. */
@FunctionalInterface
public interface VillageSystem {
	void tick(MinecraftServer server, VillageState village);
}
