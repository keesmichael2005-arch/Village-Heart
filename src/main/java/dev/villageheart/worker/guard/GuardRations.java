package dev.villageheart.worker.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.guard.GuardData;
import dev.villageheart.core.guard.GuardRation;
import dev.villageheart.food.FoodCatalog;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.job.WorkStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The guard's food ration (M18 r3, owner design): the glue around {@link GuardRation}. A refill withdraws cooked food from the village's
 * Storage (so it is counted against the food days like any meal); a bite spends one point and heals the guard. There is no other healing.
 */
final class GuardRations {
	private static final Map<UUID, Long> LAST_BITE = new HashMap<>();
	private static final Map<UUID, Long> NEXT_REFILL = new HashMap<>();
	private static final int REFILL_BACKOFF = 200;
	private static final int MAX_ITEMS_PER_REFILL = 4;

	private GuardRations() {}

	static void resetForTests() {
		LAST_BITE.clear();
		NEXT_REFILL.clear();
	}

	static void forget(UUID guard) {
		LAST_BITE.remove(guard);
		NEXT_REFILL.remove(guard);
	}

	/** One bite if the guard is hurt, has a ration and is not still chewing the last one. Returns true when it ate. */
	static boolean bite(ServerLevel level, Villager g) {
		long now = level.getGameTime();
		Long last = LAST_BITE.get(g.getUUID());
		if (last != null && now - last < Balance.GUARD_EAT_TICKS) return false;
		GuardData d = GuardAttachments.get(g);
		GuardRation.Bite b = GuardRation.bite(d.ration(), g.getHealth(), g.getMaxHealth());
		if (b.heal() <= 0) return false;
		LAST_BITE.put(g.getUUID(), now);
		GuardAttachments.set(g, d.withRation(b.ration()));
		g.heal((float) b.heal());
		WorkStats.add("guard", "food_eaten", 1);
		level.playSound(null, g.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 1.0F);
		return true;
	}

	/** Tops the ration up from the village's Storage when it is low; true when something was taken. A miss is retried after a while. */
	static boolean refill(ServerLevel level, Villager g) {
		long now = level.getGameTime();
		GuardData d = GuardAttachments.get(g);
		if (!GuardRation.wantsRefill(d.ration())) return false;
		Long next = NEXT_REFILL.get(g.getUUID());
		if (next != null && now < next) return false;
		int ration = d.ration(), taken = 0;
		for (int i = 0; i < MAX_ITEMS_PER_REFILL && ration < Balance.GUARD_RATION_CAP; i++) {
			ItemStack got = Stockpiles.withdrawFirst(level, g.blockPosition(), GuardRations::cookedFood, 1, g.getUUID());
			if (got.isEmpty()) break;
			int points = FoodCatalog.of(StackKey.of(got)).map(v -> v.nutrition()).orElse(0) * got.getCount();
			ration = GuardRation.add(ration, points);
			taken += got.getCount();
		}
		if (taken == 0) {
			NEXT_REFILL.put(g.getUUID(), now + REFILL_BACKOFF);
			return false;
		}
		NEXT_REFILL.remove(g.getUUID());
		GuardAttachments.set(g, d.withRation(ration));
		WorkStats.add("guard", "ration_items", taken);
		WorkStats.add("guard", "ration_points", ration - d.ration());
		return true;
	}

	private static boolean cookedFood(StackKey k) { return FoodCatalog.of(k).filter(v -> !v.raw()).isPresent(); }
}
