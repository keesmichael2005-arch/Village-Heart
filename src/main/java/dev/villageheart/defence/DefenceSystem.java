package dev.villageheart.defence;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Every {@link Balance#DEFENCE_REFRESH_TICKS} ticks: a recorded lamp post or watchtower whose key block is gone (in a loaded chunk) is
 * removed from the village, so it stops being a patrol point. A site in unloaded chunks is kept.
 */
public final class DefenceSystem implements VillageSystem {
	public static final DefenceSystem INSTANCE = new DefenceSystem();

	private DefenceSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (village.defences().isEmpty() || server.getTickCount() % Balance.DEFENCE_REFRESH_TICKS != 0) return;
		try (var t = VillageTicker.section()) {
			refresh(server, village);
		}
	}

	/** Removes the sites that lost a key block. Public for tests. */
	public static void refresh(MinecraftServer server, VillageState village) {
		List<DefenceSite> gone = new ArrayList<>();
		for (DefenceSite s : village.defences()) {
			ServerLevel level = Workers.levelOf(server, s.ground());
			if (level == null) continue;
			for (var key : dev.villageheart.core.defence.Defences.keys(s)) {
				BlockPos p = new BlockPos(key.at().x(), key.at().y(), key.at().z());
				if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) continue;
				String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
				if (!id.equals(key.blockId())) {
					// A breached shaft counts as the key still being there, so a broken watchtower stays a patrol point.
					long packed = dev.villageheart.core.room.Pos.pack(p.getX(), p.getY(), p.getZ());
					if (dev.villageheart.siege.SiegeDamage.isBreached(server, packed)) continue;
					gone.add(s);
					break;
				}
			}
		}
		if (gone.isEmpty()) return;
		VillageClaims.data(server).update(v -> {
			VillageState next = v;
			for (DefenceSite s : gone) next = next.withoutDefence(s.id());
			return next;
		});
		for (DefenceSite s : gone) VillageHeart.LOG.info("{} at ({}, {}, {}) is gone", s.blueprintId(), s.ground().x(), s.ground().y(), s.ground().z());
	}
}
