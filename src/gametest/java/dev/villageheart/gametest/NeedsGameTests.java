package dev.villageheart.gametest;

import static dev.villageheart.gametest.NeedsTestSupport.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.worker.Needs;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.food.FoodStats;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.WorkerAttachments;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Layer 2: food, dinner and needs (M11). Rules: see {@link NeedsTestSupport}. Dinner is triggered by moving the day time to the next day's
 * 10980 and then waiting the 20 ticks to 11000; every wait is measured against a bound.
 */
public class NeedsGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static long phase(GameTestHelper h) { return Math.floorMod(h.getLevel().getDayTime(), 24000L); }

	// ---- T-L2-1 (roadmap: 4 villagers, 20 bread) --------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_dinner_basic", structure = YARD, skyAccess = true, maxTicks = 900)
	public void dinnerFeedsFourFromTwentyBread(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<Villager>[] vs = new List[1];
		long[] t0 = new long[1];
		storageScene(h).thenExecute(() -> {
			putBread(h, 20);
			vs[0] = spawn(h, 4);
		}).thenWaitUntil(() -> h.assertValueEqual(4, census(h), "villagers in the census"))
				.thenExecute(() -> {
					h.assertValueEqual(20L, count(h, Items.BREAD), "bread before dinner");
					nextDinner(h);
				})
				.thenIdle(18)
				.thenExecute(() -> {
					h.assertTrue(phase(h) >= 10990 && phase(h) < 11000, "day phase just before dinner: " + phase(h));
					h.assertValueEqual(20L, count(h, Items.BREAD), "bread just before 11000");
					t0[0] = h.getTick();
				})
				.thenWaitUntil(() -> h.assertValueEqual(12L, count(h, Items.BREAD), "bread after dinner"))
				.thenExecute(() -> {
					long took = h.getTick() - t0[0];
					log("dinnerFeedsFourFromTwentyBread: dinner served " + took + " ticks after phase " + phase(h));
					h.assertTrue(took <= 40, "dinner took " + took + " ticks after 10990, bound 40");
					for (Villager v : vs[0]) {
						h.assertTrue(diet(h, v).fed(), "fed: " + diet(h, v));
						h.assertValueEqual(10, diet(h, v).lastNutrition(), "nutrition eaten");
						h.assertValueEqual(DinnerDay.today(h), diet(h, v).lastDinnerDay(), "the diet names today's dinner");
					}
					h.assertValueEqual(DinnerDay.today(h), lastDinnerDay(h), "last_dinner_day");
					FoodStats.Day day = FoodStats.snapshot();
					h.assertValueEqual(1L, day.dinners(), "dinners");
					h.assertValueEqual(4L, day.fed(), "fed");
					h.assertValueEqual(8L, day.eatenItems(), "items eaten");
				})
				.thenIdle(200)
				.thenExecute(() -> {
					h.assertValueEqual(12L, count(h, Items.BREAD), "no second dinner the same day");
					h.assertValueEqual(1L, FoodStats.snapshot().dinners(), "still one dinner");
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	/** Day index helpers. */
	static final class DinnerDay {
		static long today(GameTestHelper h) { return dev.villageheart.core.food.DinnerClock.dayIndex(h.getLevel().getDayTime()); }
	}

	// ---- T-L2-2 (roadmap: no food, hungry for 3 days, nobody dies) --------------------------------

	@GameTest(environment = "villageheart-gametest:needs_hungry", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void noFoodAllHungryNoDeaths(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] farmer = new Villager[1];
		List<Villager>[] others = new List[1];
		var seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			FarmTestSupport.assertRandomTicksOff(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, FarmTestSupport.FARM, 1);
			FarmTestSupport.buildStorage(h);
			FarmTestSupport.placeMarkers(h, true);
		}).thenWaitUntil(() -> FarmTestSupport.assertValid(h, true)).thenExecute(() -> {
			NeedsTestHooks.enable();
			farmer[0] = FarmTestSupport.hire(h, FarmTestSupport.spawnSpot());
			others[0] = spawn(h, 3);
		}).thenWaitUntil(() -> h.assertValueEqual(4, census(h), "villagers in the census"));
		for (int round = 1; round <= 3; round++) {
			int k = round;
			seq = seq.thenExecute(() -> nextDinner(h)).thenIdle(60).thenExecute(() -> {
				for (Villager v : others[0]) h.assertValueEqual(k, diet(h, v).hungryDays(), "hungry days of a villager after dinner " + k);
				h.assertValueEqual(k, diet(h, farmer[0]).hungryDays(), "hungry days of the farmer after dinner " + k);
				for (Villager v : others[0]) {
					h.assertTrue(v.isAlive(), "alive after dinner " + k);
					h.assertTrue(v.getHealth() >= v.getMaxHealth(), "full health after dinner " + k + ": " + v.getHealth());
				}
				WorkerData wd = farmer[0].getAttached(WorkerAttachments.WORKER);
				h.assertValueEqual(k, wd.needs().hungryDays(), "the worker's needs mirror the diet after dinner " + k);
				h.assertFalse(wd.needs().fed(), "the worker is not fed");
				h.assertValueEqual(k == 1 ? 0.5 * Balance.UNHOUSED_SPEED : 0.0, wd.needs().speed(), "work speed after dinner " + k);
				h.assertValueEqual(k >= 2 ? dev.villageheart.core.worker.WorkerStatus.TOO_HUNGRY : dev.villageheart.core.worker.WorkerStatus.OK,
						FarmTestSupport.status(h, farmer[0]), "the worker's status after dinner " + k);
			});
		}
		seq.thenExecute(() -> {
			h.assertValueEqual(3L, FoodStats.snapshot().dinners(), "three dinners");
			h.assertValueEqual(12L, FoodStats.snapshot().hungry(), "twelve hungry meals (4 villagers x 3)");
			h.assertValueEqual(0L, FoodStats.snapshot().eatenNutrition(), "nothing eaten");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	// ---- T-L2-3 ------------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_dinner_once", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void dinnerOncePerDayAndAfterReload(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		long[] day = new long[1];
		storageScene(h).thenExecute(() -> {
			putBread(h, 10);
			spawn(h, 2);
		}).thenWaitUntil(() -> h.assertValueEqual(2, census(h), "villagers in the census"))
				.thenExecute(() -> nextDinner(h))
				.thenWaitUntil(() -> h.assertValueEqual(6L, count(h, Items.BREAD), "bread after the first dinner (4 eaten)"))
				.thenExecute(() -> {
					day[0] = DinnerDay.today(h);
					h.getLevel().setDayTime(day[0] * 24000L + 10990); // back to before 11000 the same day: not due again
				})
				.thenIdle(60)
				.thenExecute(() -> {
					h.assertValueEqual(6L, count(h, Items.BREAD), "no second dinner after the clock went back");
					try {
						var reloaded = VillageTestSupport.reload(h.getLevel().getServer());
						h.assertValueEqual(day[0], reloaded.lastDinnerDay().orElse(-99), "last_dinner_day survives a save and reload");
					} catch (java.io.IOException e) {
						throw h.assertionException("reload failed: " + e);
					}
					h.getLevel().setDayTime(day[0] * 24000L + 10990);
				})
				.thenIdle(40)
				.thenExecute(() -> {
					h.assertValueEqual(6L, count(h, Items.BREAD), "still no dinner that day");
					nextDinner(h);
				})
				.thenWaitUntil(() -> h.assertValueEqual(2L, count(h, Items.BREAD), "the next day's dinner eats 4 more"))
				.thenExecute(() -> {
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	// ---- T-L2-4 ------------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_dinner_best", structure = YARD, skyAccess = true, maxTicks = 900)
	public void dinnerPicksBestFoodChildHalf(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<Villager>[] vs = new List[1];
		storageScene(h).thenExecute(() -> {
			put(h, new ItemStack(Items.BEEF, 2), new ItemStack(Items.COOKED_BEEF, 1), new ItemStack(Items.BREAD, 1), new ItemStack(Items.BEETROOT_SOUP, 1));
			vs[0] = spawn(h, 2);
			vs[0].get(1).setAge(-24000);
		}).thenWaitUntil(() -> h.assertValueEqual(2, census(h), "villagers in the census"))
				.thenExecute(() -> {
					h.assertTrue(vs[0].get(1).isBaby(), "the second villager is a baby");
					nextDinner(h);
				})
				.thenWaitUntil(() -> h.assertValueEqual(0L, count(h, Items.COOKED_BEEF), "the cooked beef was eaten"))
				.thenExecute(() -> {
					h.assertValueEqual(0L, count(h, Items.BEETROOT_SOUP), "the beetroot soup was eaten (prepared before raw, highest first)");
					h.assertValueEqual(0L, count(h, Items.BREAD), "the bread was eaten (by the child)");
					h.assertValueEqual(2L, count(h, Items.BEEF), "raw beef untouched");
					h.assertValueEqual(1L, count(h, Items.BOWL), "the soup's bowl is in the stockpile");
					h.assertValueEqual(14, diet(h, vs[0].get(0)).lastNutrition(), "the adult ate beef (8) and soup (6)");
					h.assertValueEqual(5, diet(h, vs[0].get(1)).lastNutrition(), "the child ate the bread (5)");
					h.assertTrue(diet(h, vs[0].get(0)).fed() && diet(h, vs[0].get(1)).fed(), "both fed");
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	@GameTest(environment = "villageheart-gametest:needs_dinner_raw", structure = YARD, skyAccess = true, maxTicks = 900)
	public void dinnerEatsPreparedBeforeRicherRaw(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<Villager>[] vs = new List[1];
		storageScene(h).thenExecute(() -> {
			put(h, new ItemStack(Items.BEEF, 10), new ItemStack(Items.COOKIE, 5));
			vs[0] = spawn(h, 1);
		}).thenWaitUntil(() -> h.assertValueEqual(1, census(h), "villagers in the census"))
				.thenExecute(() -> nextDinner(h))
				.thenWaitUntil(() -> h.assertValueEqual(0L, count(h, Items.COOKIE), "the cookies were eaten"))
				.thenExecute(() -> {
					h.assertValueEqual(10L, count(h, Items.BEEF), "the raw beef (3 each, richer than a cookie) is untouched");
					h.assertTrue(diet(h, vs[0].get(0)).fed(), "fed");
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	// ---- T-L2-10 ------------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_dinner_edge", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void dinnerEdgeCases(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<Villager>[] vs = new List[1];
		// (a) no Storage at all
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			NeedsTestHooks.enable();
			vs[0] = spawn(h, 2);
			// (c) neither a wandering trader nor a zombie villager eats
			var trader = h.spawn(EntityType.WANDERING_TRADER, BuildingTestSupport.inside(FarmTestSupport.STORE, 3, 0, 3));
			trader.setNoAi(true);
			var zombie = h.spawn(EntityType.ZOMBIE_VILLAGER, BuildingTestSupport.inside(FarmTestSupport.STORE, 4, 0, 3));
			zombie.setNoAi(true);
			zombie.setPersistenceRequired();
		}).thenWaitUntil(() -> h.assertValueEqual(2, census(h), "only the two villagers are in the census"))
				.thenExecute(() -> nextDinner(h))
				.thenIdle(60)
				.thenExecute(() -> {
					FoodStats.Day d = FoodStats.snapshot();
					h.assertValueEqual(1L, d.dinners(), "dinner ran without any Storage");
					h.assertValueEqual(2L, d.diners(), "diners: the trader and the zombie villager do not eat");
					h.assertValueEqual(2L, d.hungry(), "both hungry with no Storage");
					for (Villager v : vs[0]) h.assertValueEqual(1, diet(h, v).hungryDays(), "hungry days");
					// (b) build the Storage now: 10 bread of which 6 are reserved by somebody else
					FarmTestSupport.buildStorage(h);
					BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, FarmTestSupport.storeMarker(), net.minecraft.core.Direction.NORTH);
				})
				.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, FarmTestSupport.storeMarker(), dev.villageheart.block.MarkerStatus.VALID))
				.thenExecute(() -> {
					putBread(h, 10);
					Stockpiles.reservations(h.getLevel().getServer()).set(UUID.randomUUID(), StackKey.of(Items.BREAD), 6);
					FoodStats.reset();
					nextDinner(h);
				})
				.thenWaitUntil(() -> h.assertValueEqual(6L, count(h, Items.BREAD), "only the 4 unreserved bread were eaten"))
				.thenExecute(() -> {
					h.assertValueEqual(2L, FoodStats.snapshot().fed(), "both fed from the 4 unreserved bread");
					for (Villager v : vs[0]) h.assertValueEqual(0, diet(h, v).hungryDays(), "hunger reset after a meal");
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}

	// ---- T-L2-14 ------------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:needs_existing_unaffected", structure = YARD, skyAccess = true, maxTicks = 900)
	public void existingWorkerTestsUnaffected(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] farmer = new Villager[1];
		BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			WorkerTestSupport.farmhouse(h, FarmTestSupport.FARM, 1);
			FarmTestSupport.placeMarkers(h, false);
		}).thenWaitUntil(() -> FarmTestSupport.assertValid(h, false)).thenExecute(() -> {
			// the hooks stay at their reset defaults: default needs, no dinner
			farmer[0] = FarmTestSupport.hire(h, FarmTestSupport.spawnSpot());
			h.getLevel().setDayTime((dev.villageheart.core.food.DinnerClock.dayIndex(h.getLevel().getDayTime()) + 1) * 24000L + 10980);
		}).thenIdle(Balance.NEEDS_REFRESH_TICKS + 40).thenExecute(() -> {
			WorkerData wd = farmer[0].getAttached(WorkerAttachments.WORKER);
			h.assertValueEqual(Needs.DEFAULT, wd.needs(), "a worker with no House and no Storage keeps default needs");
			h.assertTrue(phase(h) > 11000, "the clock passed 11000: " + phase(h));
			h.assertValueEqual(0L, FoodStats.snapshot().dinners(), "no dinner ran");
			h.assertTrue(diet(h, farmer[0]).fed() && diet(h, farmer[0]).lastDinnerDay() == -1, "the diet is untouched: " + diet(h, farmer[0]));
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}
}
