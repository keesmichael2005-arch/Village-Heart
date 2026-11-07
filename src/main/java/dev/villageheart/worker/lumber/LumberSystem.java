package dev.villageheart.worker.lumber;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.lumber.ReplantRules;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;

/**
 * Keeps the saved tree tasks honest, every 100 ticks: a task whose building is gone is dropped; a felling task whose base cells hold no
 * log any more becomes a replant task; a replant task nobody planted for {@link Balance#REPLANT_EXPIRY_DAYS} days is dropped (logged once).
 */
public final class LumberSystem implements VillageSystem {
	public static final LumberSystem INSTANCE = new LumberSystem();
	private static final int INTERVAL = 100;

	private LumberSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (village.treeTasks().isEmpty() || server.getTickCount() % INTERVAL != 0) return;
		long now = server.overworld().getGameTime();
		List<TreeTask> drop = new ArrayList<>();
		List<TreeTask> toReplant = new ArrayList<>();
		LumberRuntime rt = LumberRuntime.get(server);
		for (TreeTask t : village.treeTasks()) {
			if (!Workers.jobSites().isRegistered(server, t.building())) {
				drop.add(t);
				VillageHeart.LOG.info("Dropped the tree task at {}: its Lumber Camp is gone", t.key());
				continue;
			}
			if (t.stage() == TreeTask.Stage.REPLANT) {
				if (ReplantRules.expired(t.since(), now)) {
					drop.add(t);
					VillageHeart.LOG.info("Dropped the replant spot at {}: nobody could plant it in {} days", t.key(), Balance.REPLANT_EXPIRY_DAYS);
				}
				continue;
			}
			if (rt.claims.containsKey(t.key()) && rt.claimedByOther(t.key(), new java.util.UUID(0, 0), now)) continue; // being felled right now
			if (baseHasNoLog(server, t)) toReplant.add(t);
		}
		if (drop.isEmpty() && toReplant.isEmpty()) return;
		VillageClaims.data(server).update(s -> {
			VillageState cur = s;
			for (TreeTask t : drop) cur = cur.withoutTreeTask(t.key());
			for (TreeTask t : toReplant) {
				if (cur.treeTask(t.key()).isEmpty()) continue;
				cur = cur.withoutTreeTask(t.key()).withTreeTask(t.withStage(TreeTask.Stage.REPLANT, t.base()).withSince(now));
				rt.claims.remove(t.key());
			}
			return cur;
		});
	}

	/** True when every base cell is in a loaded chunk and holds no log. */
	private static boolean baseHasNoLog(MinecraftServer server, TreeTask t) {
		for (HeartLocation b : t.base()) {
			ServerLevel level = Workers.levelOf(server, b);
			if (level == null) return false;
			BlockPos p = new BlockPos(b.x(), b.y(), b.z());
			if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) return false;
			if (level.getBlockState(p).is(BlockTags.OVERWORLD_NATURAL_LOGS)) return false;
		}
		return true;
	}
}
