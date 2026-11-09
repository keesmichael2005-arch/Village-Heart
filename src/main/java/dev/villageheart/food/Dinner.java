package dev.villageheart.food;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.food.DinnerPlan;
import dev.villageheart.core.food.DinnerPlanner;
import dev.villageheart.core.food.Eater;
import dev.villageheart.core.food.FoodMath;
import dev.villageheart.core.food.FoodStock;
import dev.villageheart.core.food.Meal;
import dev.villageheart.core.needs.Diet;
import dev.villageheart.core.stock.Reservations;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.needs.NeedsSystem;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.Block;

/**
 * The village eats (M11 plan 3.2 #1-#5, #11): at 11000 every villager in the claim gets its meal straight out of the Storage chests, wherever it
 * stands. Nothing is walked; the stockpile is the only food source (food in a villager's own inventory is never eaten here).
 */
public final class Dinner {
	private Dinner() {}

	/** Every kind of food in the stockpile the village eats. With {@code net} the build reservations of other systems are left out. */
	public static List<FoodStock<StackKey>> stock(MinecraftServer server, boolean net) {
		StockSnapshot<StackKey> snap = Stockpiles.snapshot(server);
		Reservations<StackKey> reserved = net ? Stockpiles.reservations(server) : null;
		List<FoodStock<StackKey>> out = new ArrayList<>();
		for (StockSnapshot.Total<StackKey> t : snap.totals()) {
			var value = FoodCatalog.of(t.key());
			if (value.isEmpty()) continue;
			long available = t.count() - (reserved == null ? 0 : reserved.reserved(t.key()));
			if (available > 0) out.add(new FoodStock<>(t.key(), t.key().id(), value.get(), available));
		}
		return out;
	}

	/** Nutrition in the stockpile (what the Heart and the L3 metric call food). */
	public static long nutrition(MinecraftServer server) { return FoodMath.nutrition(stock(server, false)); }

	/** After the harness resets the clock, day index 0 serves its dinner again. */
	public static void resetClock(MinecraftServer server) { VillageClaims.data(server).setLastDinnerDay(-1); }

	/** Serves the dinner of day index {@code day}. Server thread. */
	public static DinnerPlan<StackKey> serve(ServerLevel level, VillageState village, long day) {
		MinecraftServer server = level.getServer();
		List<Villager> census = VillageCensus.of(server, village);
		Map<UUID, Integer> workerIndex = new HashMap<>();
		int idx = 0;
		for (WorkerRecord w : village.workers()) workerIndex.put(w.villager(), idx++);
		long claim = village.claimedAtGameTime();
		List<Eater> eaters = new ArrayList<>();
		Map<UUID, Villager> byId = new HashMap<>();
		int adults = 0, children = 0;
		for (Villager v : census) {
			Diet d = DietAttachments.read(v, claim);
			eaters.add(new Eater(v.getUUID(), v.isBaby(), d.hungryDays(), workerIndex.getOrDefault(v.getUUID(), -1)));
			byId.put(v.getUUID(), v);
			if (v.isBaby()) children++; else adults++;
		}
		List<FoodStock<StackKey>> stock = stock(server, true);
		DinnerPlan<StackKey> plan = DinnerPlanner.plan(stock, eaters);
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());

		// take the food out of the chests; a shortfall (impossible within one tick, cheap to guard) leaves the last eaters of that kind hungry
		Map<StackKey, Integer> short_ = new HashMap<>();
		for (Map.Entry<StackKey, Long> e : plan.consumed().entrySet()) {
			long left = e.getValue();
			while (left > 0) {
				ItemStack got = Stockpiles.withdraw(level, heart, e.getKey(), (int) Math.min(left, e.getKey().maxStackSize()), null);
				if (got.isEmpty()) break;
				left -= got.getCount();
				giveRemainder(level, heart, got);
			}
			if (left > 0) short_.put(e.getKey(), (int) left);
		}
		Map<UUID, Boolean> fed = new HashMap<>();
		for (Meal<StackKey> m : plan.meals()) fed.put(m.eater(), m.fed());
		if (!short_.isEmpty()) {
			List<Meal<StackKey>> reversed = new ArrayList<>(plan.meals());
			java.util.Collections.reverse(reversed);
			for (Map.Entry<StackKey, Integer> s : short_.entrySet()) {
				int missing = s.getValue();
				for (Meal<StackKey> m : reversed) {
					if (missing <= 0) break;
					int n = m.items().getOrDefault(s.getKey(), 0);
					if (n > 0 && fed.get(m.eater())) {
						fed.put(m.eater(), false);
						missing -= n;
					}
				}
			}
			VillageHeart.LOG.warn("[dinner] day {}: the chests held less than planned: {}", day, short_);
		}

		int fedCount = 0;
		for (Meal<StackKey> m : plan.meals()) {
			Villager v = byId.get(m.eater());
			boolean ok = fed.get(m.eater());
			if (ok) fedCount++;
			DietAttachments.write(v, DietAttachments.read(v, claim).afterDinner(ok, ok ? m.nutrition() : 0, day));
		}
		VillageClaims.data(server).setLastDinnerDay(day);
		NeedsSystem.mirror(server, VillageClaims.current(server).orElse(village));
		long eatenNutrition = plan.eatenNutrition(), eatenItems = plan.eatenItems();
		FoodStats.record(plan.diners(), fedCount, plan.diners() - fedCount, eatenNutrition, eatenItems);
		OptionalDouble daysLeft = FoodMath.daysLeft(FoodMath.nutrition(stock(server, false)), adults, children);
		VillageHeart.LOG.info("[dinner] day {}: {}/{} fed, {} nutrition from {} items, {} food days left", day, fedCount, plan.diners(),
				eatenNutrition, eatenItems, daysLeft.isPresent() ? String.format("%.1f", daysLeft.getAsDouble()) : "-");
		return plan;
	}

	/** An eaten stew leaves its bowl: into the stockpile, or popped at the Heart when that is full. */
	private static void giveRemainder(ServerLevel level, BlockPos heart, ItemStack eaten) {
		UseRemainder remainder = eaten.get(DataComponents.USE_REMAINDER);
		if (remainder == null) return;
		ItemStack rem = remainder.convertInto().copyWithCount(eaten.getCount() * remainder.convertInto().getCount());
		if (rem.isEmpty()) return;
		Stockpiles.deposit(level, heart, rem);
		if (!rem.isEmpty()) Block.popResource(level, heart.above(), rem);
	}

	/** Which level the village's dinner is served in, or null when it is not loaded. */
	public static ServerLevel levelOf(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return null;
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		return level.isLoaded(heart) ? level : null;
	}
}
