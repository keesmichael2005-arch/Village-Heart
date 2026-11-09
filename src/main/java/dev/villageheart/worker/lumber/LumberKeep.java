package dev.villageheart.worker.lumber;

import dev.villageheart.core.lumber.ReplantRules;
import dev.villageheart.worker.WorkerAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;

/** What a lumberjack keeps when it hands in its load: the saplings the camp's open replant spots need, plus a small reserve. */
public final class LumberKeep {
	private LumberKeep() {}

	public static int keep(Villager v, Item item) {
		if (!item.getDefaultInstance().is(ItemTags.SAPLINGS)) return 0;
		if (!(v.level() instanceof ServerLevel level)) return 0;
		var data = WorkerAttachments.get(v);
		if (data.isEmpty()) return 0;
		int have = v.getInventory().countItem(item);
		return ReplantRules.keepCount(have, LumberJob.saplingsNeeded(level.getServer(), data.get(), item));
	}
}
