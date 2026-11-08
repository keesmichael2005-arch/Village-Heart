package dev.villageheart.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** What the M11 scenario actions need from their runner: the simulation, or a GameTest standing in for it. */
public interface Host {
	ServerLevel level();

	MinecraftServer server();

	/** The Heart, or null before the claim. */
	BlockPos heart();

	/** The origin (min corner, ground level) of the flattened site, or null before the {@code site} action. */
	BlockPos site();

	void setSite(BlockPos site);
}
