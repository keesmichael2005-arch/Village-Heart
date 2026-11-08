package dev.villageheart.worker.build;

import dev.villageheart.build.BuildJobs;
import dev.villageheart.core.blueprint.BuildJob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;

/**
 * What a builder keeps when it hands in its load: the materials it fetched for the build it carries for (their count is saved with the build).
 * Everything else it holds (the drops of clearing, the empty bucket) is spoil and goes to Storage.
 */
public final class BuilderKeep {
	private BuilderKeep() {}

	public static int keep(Villager v, Item item) {
		if (!(v.level() instanceof ServerLevel level)) return 0;
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		int n = 0;
		for (BuildJob b : BuildJobs.list(level.getServer())) if (v.getUUID().equals(b.carrier())) n += b.carriedOf(id);
		return n;
	}
}
