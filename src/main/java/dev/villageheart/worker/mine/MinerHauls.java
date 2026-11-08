package dev.villageheart.worker.mine;

import dev.villageheart.worker.job.AwayHauls;
import java.util.Map;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Hands a haul to the miner. The villager inventory is the delivery: what does not fit (kept food in several slots) is not dropped, it
 * stays in the saved pending items and goes into the inventory as soon as there is room (M16 plan 3.2 #9). The body is the job-neutral
 * {@link AwayHauls} since M17.
 */
public final class MinerHauls {
	private MinerHauls() {}

	/** Puts the haul into the inventory, counts every item as mined, and saves what did not fit. */
	public static void give(Villager v, Map<String, Integer> haul) { AwayHauls.give(v, MinerJob.JOB, haul); }

	/** Moves what was waiting for room into the inventory; whatever still does not fit stays pending. */
	public static void flushPending(Villager v) { AwayHauls.flushPending(v); }
}
