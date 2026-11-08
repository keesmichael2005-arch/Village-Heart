package dev.villageheart.worker.mine;

import dev.villageheart.core.mine.AwayState;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.WorkStats;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hands a haul to the miner. The villager inventory is the delivery: what does not fit (kept food in several slots) is not dropped, it
 * stays in the saved {@link AwayState#pending()} and goes into the inventory as soon as there is room (M16 plan 3.2 #9).
 */
public final class MinerHauls {
	private MinerHauls() {}

	/** Puts the haul into the inventory, counts every item as mined, and saves what did not fit. */
	public static void give(Villager v, Map<String, Integer> haul) {
		Map<String, Integer> left = new LinkedHashMap<>(AwayTrips.state(v).pending());
		for (Map.Entry<String, Integer> e : haul.entrySet()) {
			WorkStats.add(MinerJob.JOB, e.getKey().substring(e.getKey().indexOf(':') + 1), e.getValue());
			int rest = insert(v, e.getKey(), e.getValue());
			if (rest > 0) left.merge(e.getKey(), rest, Integer::sum);
		}
		AwayTrips.set(v, AwayTrips.state(v).withPending(left));
	}

	/** Moves what was waiting for room into the inventory; whatever still does not fit stays pending. */
	public static void flushPending(Villager v) {
		AwayState st = AwayTrips.state(v);
		if (st.pending().isEmpty()) return;
		Map<String, Integer> left = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : st.pending().entrySet()) {
			int rest = insert(v, e.getKey(), e.getValue());
			if (rest > 0) left.put(e.getKey(), rest);
		}
		if (!left.equals(st.pending())) AwayTrips.set(v, st.withPending(left));
	}

	/** @return how many of the {@code count} items did not fit */
	private static int insert(Villager v, String id, int count) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		SimpleContainer inv = v.getInventory();
		int left = count;
		while (left > 0) {
			ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
			ItemStack rest = inv.addItem(stack);
			left -= stack.getCount() - rest.getCount();
			if (!rest.isEmpty()) return left;
		}
		return 0;
	}
}
