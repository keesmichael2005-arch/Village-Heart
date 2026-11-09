package dev.villageheart.gametest;

import static dev.villageheart.gametest.CookTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.cook.CookRecipe;
import dev.villageheart.core.cook.CookRecipes;
import dev.villageheart.core.cook.FuelTable;
import dev.villageheart.core.cook.Ingredient;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.cook.CookJob;
import dev.villageheart.worker.cook.CookRuntime;
import dev.villageheart.worker.job.WorkStats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Layer 2: the cook (M13). Rules (docs/TESTING.md, "Cook GameTests"): own {@code cook_<name>} environment, structure {@code yard} with
 * {@code skyAccess}, {@link CookTestSupport#scene} first, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last, work tests start
 * at day time 1000, exact counts and measured bounds. Roadmap tests log their ticks ({@code [m13] ...}).
 */
public class CookGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	/** Mutation probes (reviewer r1): flip to true to see a watch go red. */
	private static final boolean PROBE = false;

	private static void finish(GameTestHelper h) {
		VillageTestSupport.done(h);
		end(h);
	}

	// ---- T-L2-1 (roadmap) ----------------------------------------------------------------------

	/** 10 raw beef, 9 wheat and 4 coal in Storage + a cook: 10 cooked beef and 3 bread before dinner, the raw beef gone, 2 coal left, 600 heat banked. */
	@GameTest(environment = "villageheart-gametest:cook_roadmap", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void cooksBeefAndBread(GameTestHelper h) {
		long[] t0 = new long[1];
		long[] items0 = new long[1];
		boolean[] sawLit = new boolean[1];
		Villager[] cook = new Villager[1];
		BuildingTestSupport.awaitWithin(h, scene(h).thenExecute(() -> {
			putStore(h, Items.BEEF, 10);
			putStore(h, Items.WHEAT, 9);
			putStore(h, Items.COAL, 4);
			setDayTime(h, 1000);
			t0[0] = h.getTick();
			items0[0] = WorkStats.get("cook", "items");
			cook[0] = hire(h);
		}), cookBound(13, 3), "cooking 13 items", () -> {
			if (lit(h)) sawLit[0] = true;
			h.assertTrue(count(h, Items.COOKED_BEEF) == 10 && count(h, Items.BREAD) == 3, "cooked " + count(h, Items.COOKED_BEEF) + " beef, "
					+ count(h, Items.BREAD) + " bread; status " + status(h, cook[0]) + " batch " + CookJob.batchForTest(h.getLevel().getServer(), cook[0].getUUID()));
		}).thenExecute(() -> {
			log("cooksBeefAndBread: 13 items in " + (h.getTick() - t0[0]) + " ticks");
			h.assertTrue(Math.floorMod(h.getLevel().getDayTime(), 24000L) < 10500, "before the cutoff");
			h.assertTrue(count(h, Items.BEEF) == 0, "raw beef gone");
			h.assertTrue(count(h, Items.WHEAT) == 0, "wheat gone");
			h.assertTrue(count(h, Items.COAL) == 2, "coal left " + count(h, Items.COAL));
			h.assertTrue(bank(h) == 600, "heat bank " + bank(h));
			h.assertTrue(sawLit[0], "the smoker was lit while cooking");
			h.assertTrue(WorkStats.get("cook", "items") - items0[0] == 13, "work.cook.items " + (WorkStats.get("cook", "items") - items0[0]));
			h.assertTrue(itemEntities(h) == 0, "nothing was dropped into the world");
			h.assertValueEqual((int) (13L * dev.villageheart.core.Balance.milliXpPerUnit(dev.villageheart.core.job.WorkUnit.ITEM_COOKED) / 1000),
					dev.villageheart.worker.WorkerAttachments.get(cook[0]).orElseThrow().xp(), "M15: XP per output item (ITEM_COOKED)");
		}).thenWaitUntil(() -> {
			h.assertTrue(carried(cook[0], Items.COOKED_BEEF) == 0 && carried(cook[0], Items.BREAD) == 0 && carried(cook[0], Items.COAL) == 0
					&& carried(cook[0], Items.BEEF) == 0, "the cook's hands are empty: " + cook[0].getInventory());
			h.assertTrue(reservedBy(h, cook[0].getUUID(), Items.BEEF) == 0 && reservedBy(h, cook[0].getUUID(), Items.COAL) == 0, "no reservation left");
		}).thenWaitUntil(() -> h.assertFalse(lit(h), "the smoker is unlit when the batch is over")).thenExecute(() -> finish(h));
	}

	// ---- T-L2-2 (roadmap) ----------------------------------------------------------------------

	/** No fuel in the stockpile (coal sits in the Kitchen chest): nothing is cooked and the reason is "Needs fuel"; one coal in Storage starts it. */
	@GameTest(environment = "villageheart-gametest:cook_nofuel", structure = YARD, skyAccess = true, maxTicks = 4200)
	public void noFuelNothingCooked(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		long[] t0 = new long[1];
		int[] samples = new int[1];
		boolean[] watch = new boolean[1];
		List<String> problems = new ArrayList<>();
		// a failed assertion inside thenWaitUntil is swallowed and retried: the watch records its findings and asserts the list afterwards
		h.onEachTick(() -> {
			if (!watch[0] || cook[0] == null || (h.getTick() - t0[0]) % 20 != 0) return;
			samples[0]++;
			if (count(h, Items.BEEF) != 10 || count(h, Items.WHEAT) != 9) problems.add("@" + h.getTick() + " raw food moved");
			if (count(h, Items.COOKED_BEEF) != 0 || count(h, Items.BREAD) != 0 || carried(cook[0], Items.COOKED_BEEF) != 0) problems.add("@" + h.getTick() + " something was cooked");
			if (carried(cook[0], Items.BEEF) != 0 || carried(cook[0], Items.WHEAT) != 0) problems.add("@" + h.getTick() + " raw food carried");
			if (lit(h)) problems.add("@" + h.getTick() + " the smoker is lit");
			if (itemEntities(h) != 0) problems.add("@" + h.getTick() + " an item was dropped");
			if (reservedBy(h, cook[0].getUUID(), Items.BEEF) != 0) problems.add("@" + h.getTick() + " a reservation is held");
			if (PROBE) problems.add("probe");
		});
		scene(h).thenExecute(() -> {
			putStore(h, Items.BEEF, 10);
			putStore(h, Items.WHEAT, 9);
			put(h, Items.COAL, 4, kitchenChest());
			setDayTime(h, 1000);
			cook[0] = hire(h);
			t0[0] = h.getTick();
		}).thenWaitUntil(() -> h.assertTrue(status(h, cook[0]) == WorkerStatus.NEEDS_FUEL, "status " + status(h, cook[0])))
				.thenExecute(() -> {
					log("noFuelNothingCooked: Needs fuel after " + (h.getTick() - t0[0]) + " ticks");
					h.assertTrue(h.getTick() - t0[0] <= 200, "the reason shows within 200 ticks");
					h.assertValueEqual("Needs fuel", dev.villageheart.worker.job.WorkerStatusText.of(WorkerStatus.NEEDS_FUEL, dev.villageheart.core.worker.JobType.COOK).getString(), "text");
					t0[0] = h.getTick();
				})
				.thenExecute(() -> {
					t0[0] = h.getTick();
					watch[0] = true;
				})
				.thenIdle(3000)
				.thenExecute(() -> {
					watch[0] = false;
					h.assertTrue(problems.isEmpty(), "the no-fuel watch saw: " + problems);
					h.assertTrue(samples[0] >= 25, "sampled " + samples[0]);
					h.assertTrue(((net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(kitchenChest()))).countItem(Items.COAL) == 4, "the Kitchen chest's coal is not fuel");
					putStore(h, Items.COAL, 1);
					t0[0] = h.getTick();
				})
				.thenWaitUntil(() -> h.assertTrue(status(h, cook[0]) != WorkerStatus.NEEDS_FUEL, "still " + status(h, cook[0])))
				.thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) >= 1 || carried(cook[0], Items.COOKED_BEEF) >= 1, "the first cooked beef"))
				.thenExecute(() -> {
					log("noFuelNothingCooked: first beef " + (h.getTick() - t0[0]) + " ticks after the coal");
					h.assertTrue(h.getTick() - t0[0] <= cookBound(1, 1), "the first item within the bound");
					finish(h);
				});
	}

	// ---- T-L2-3 fuel kinds -----------------------------------------------------------------------

	/** Logs burn (300 each): 8 potatoes + 10 oak logs make 8 baked potatoes with 6 logs; 200 heat stay in the bank. */
	@GameTest(environment = "villageheart-gametest:cook_logs", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void logsAreFuel(GameTestHelper h) {
		scene(h).thenExecute(() -> {
			putStore(h, Items.POTATO, 8);
			putStore(h, Items.OAK_LOG, 10);
			setDayTime(h, 1000);
			hire(h);
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.BAKED_POTATO) == 8, "baked " + count(h, Items.BAKED_POTATO)))
				.thenWaitUntil(() -> h.assertTrue(count(h, Items.OAK_LOG) == 4, "logs left " + count(h, Items.OAK_LOG)))
				.thenExecute(() -> {
					h.assertTrue(bank(h) == 200, "bank " + bank(h));
					finish(h);
				});
	}

	/** Coal is burnt before logs: 8 beef + 1 coal + 10 logs leaves the logs alone. */
	@GameTest(environment = "villageheart-gametest:cook_coalfirst", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void coalBeforeLogs(GameTestHelper h) {
		scene(h).thenExecute(() -> {
			putStore(h, Items.BEEF, 8);
			putStore(h, Items.COAL, 1);
			putStore(h, Items.OAK_LOG, 10);
			setDayTime(h, 1000);
			hire(h);
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) == 8, "cooked " + count(h, Items.COOKED_BEEF)))
				.thenExecute(() -> {
					h.assertTrue(count(h, Items.COAL) == 0, "the coal was used");
					h.assertTrue(count(h, Items.OAK_LOG) == 10, "the logs were not");
					h.assertTrue(bank(h) == 0, "bank " + bank(h));
					finish(h);
				});
	}

	/** A birch log (300) is one unit; a crimson stem is no fuel; the rest of the beef goes back and the cook reports "Needs fuel". */
	@GameTest(environment = "villageheart-gametest:cook_stems", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void aNetherStemIsNoFuel(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			putStore(h, Items.BEEF, 8);
			putStore(h, Items.BIRCH_LOG, 1);
			putStore(h, Items.CRIMSON_STEM, 1);
			setDayTime(h, 1000);
			cook[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) == 1 && count(h, Items.BEEF) == 7, "1 cooked, 7 raw: " + count(h, Items.COOKED_BEEF) + "/" + count(h, Items.BEEF)))
				.thenWaitUntil(() -> h.assertTrue(status(h, cook[0]) == WorkerStatus.NEEDS_FUEL && carried(cook[0], Items.BEEF) == 0, "status " + status(h, cook[0])))
				.thenExecute(() -> {
					h.assertTrue(count(h, Items.CRIMSON_STEM) == 1, "the stem was not burnt");
					h.assertTrue(bank(h) == 100, "bank " + bank(h));
					finish(h);
				});
	}

	// ---- T-L2-4 pace -------------------------------------------------------------------------------

	/** A cook at its smoker with 8 beef and a coal in hand makes one item every 125 ticks (8 per hour); at level 2 every 100. */
	@GameTest(environment = "villageheart-gametest:cook_pace", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void paceEightPerHour(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			setDayTime(h, 1000);
			cook[0] = hire(h, BuildingTestSupport.inside(KITCHEN, 1, 0, 0));
			cook[0].getInventory().addItem(new ItemStack(Items.BEEF, 8));
			cook[0].getInventory().addItem(new ItemStack(Items.COAL, 1));
		}).thenWaitUntil(() -> h.assertTrue(unitsOf(h, cook[0]).size() >= 8, "units " + unitsOf(h, cook[0]).size()))
				.thenExecute(() -> {
					List<Long> t = unitsOf(h, cook[0]);
					for (int i = 1; i < 8; i++) {
						long gap = t.get(i) - t.get(i - 1);
						h.assertTrue(Math.abs(gap - 125) <= 1, "gap " + i + " is " + gap);
					}
					log("paceEightPerHour: gaps " + t);
					// level 2 (x1.25): 100 ticks
					Workers_setLevel(h, cook[0], 2);
					cook[0].getInventory().addItem(new ItemStack(Items.BEEF, 4));
					cook[0].getInventory().addItem(new ItemStack(Items.COAL, 1));
					CookJob.replanForTest(h.getLevel().getServer(), cook[0].getUUID());
				})
				.thenWaitUntil(() -> h.assertTrue(unitsOf(h, cook[0]).size() >= 12, "units " + unitsOf(h, cook[0]).size()))
				.thenExecute(() -> {
					List<Long> t = unitsOf(h, cook[0]);
					for (int i = 9; i < 12; i++) {
						long gap = t.get(i) - t.get(i - 1);
						h.assertTrue(Math.abs(gap - 100) <= 1, "level 2 gap " + i + " is " + gap);
					}
					finish(h);
				});
	}

	private static void Workers_setLevel(GameTestHelper h, Villager v, int level) {
		dev.villageheart.worker.Workers.setLevel(h.getLevel().getServer(), v, level);
	}

	private static List<Long> unitsOf(GameTestHelper h, Villager cook) {
		List<Long> out = new ArrayList<>();
		for (var u : CookRuntime.get(h.getLevel().getServer()).unitLog()) if (u.cook().equals(cook.getUUID())) out.add(u.tick());
		return out;
	}

	// ---- T-L2-8 keep and hand back -------------------------------------------------------------------

	/** A cook holding finished food hands it all in (no food top-up for a cook); the inputs of a batch stay in its pockets until the work day ends. */
	@GameTest(environment = "villageheart-gametest:cook_keep", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void keepAndHandBack(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			setDayTime(h, 3000);
			cook[0] = hire(h);
			cook[0].getInventory().addItem(new ItemStack(Items.COOKED_BEEF, 8));
			cook[0].getInventory().addItem(new ItemStack(Items.BREAD, 3));
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) == 8 && count(h, Items.BREAD) == 3, "handed in " + count(h, Items.COOKED_BEEF) + "/" + count(h, Items.BREAD)))
				.thenExecute(() -> {
					h.assertTrue(carried(cook[0], Items.BREAD) == 0, "no bread kept by the food top-up");
					// phase 2: a batch in hand (beef + coal): no trip for 400 ticks
					cook[0].getInventory().addItem(new ItemStack(Items.BEEF, 8));
					cook[0].getInventory().addItem(new ItemStack(Items.COAL, 1));
				})
				.thenIdle(400)
				.thenExecute(() -> {
					h.assertTrue(count(h, Items.BEEF) == 0 && count(h, Items.COAL) == 0, "the batch's inputs were not handed in mid-batch");
					// phase 3: the work day ends: everything goes back
					cook[0].getInventory().addItem(new ItemStack(Items.BEEF, 4));
					cook[0].getInventory().addItem(new ItemStack(Items.COAL, 1));
					setDayTime(h, 11050);
				})
				.thenWaitUntil(() -> h.assertTrue(carried(cook[0], Items.BEEF) == 0 && carried(cook[0], Items.COAL) == 0 && count(h, Items.BEEF) + count(h, Items.COOKED_BEEF) >= 4,
						"at GO_HOME the cook keeps nothing: beef " + count(h, Items.BEEF) + " cooked " + count(h, Items.COOKED_BEEF)))
				.thenExecute(() -> finish(h));
	}

	// ---- T-L2-9 tables ------------------------------------------------------------------------------

	/** The core tables equal the game: nutrition components, the smoking recipes, the furnace burn times, the log tag. */
	@GameTest(environment = "villageheart-gametest:cook_tables", structure = YARD, skyAccess = true, maxTicks = 200)
	public void tablesMatchVanilla(GameTestHelper h) {
		var level = h.getLevel();
		for (CookRecipe r : CookRecipes.ALL) {
			Item out = BuiltInRegistries.ITEM.getValue(Identifier.parse(r.outputId()));
			h.assertTrue(out != Items.AIR, "output exists: " + r.outputId());
			var food = out.components().get(DataComponents.FOOD);
			h.assertTrue(food != null && food.nutrition() == r.outputNutrition(), r.outputId() + " nutrition " + (food == null ? null : food.nutrition()));
			int in = 0;
			for (Ingredient i : r.inputs()) {
				Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(i.itemId()));
				h.assertTrue(item != Items.AIR, "input exists: " + i.itemId());
				var f = item.components().get(DataComponents.FOOD);
				in += (f == null ? 0 : f.nutrition()) * i.count();
			}
			h.assertTrue(in == r.inputNutrition(), r.id() + " input nutrition " + in + " vs " + r.inputNutrition());
			if (r.kind() == dev.villageheart.core.cook.RecipeKind.SMOKE) {
				Item input = BuiltInRegistries.ITEM.getValue(Identifier.parse(r.inputs().get(0).itemId()));
				var si = new SingleRecipeInput(new ItemStack(input));
				var found = level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMOKING, si, level);
				h.assertTrue(found.isPresent(), "a smoking recipe for " + r.inputs().get(0).itemId());
				h.assertTrue(found.get().value().assemble(si, level.registryAccess()).is(out), "the smoker makes " + r.outputId() + " from " + r.inputs().get(0).itemId());
			}
		}
		for (Item fuel : List.of(Items.COAL, Items.CHARCOAL)) {
			h.assertValueEqual(level.fuelValues().burnDuration(new ItemStack(fuel)), FuelTable.heat(BuiltInRegistries.ITEM.getKey(fuel).toString(), false).getAsInt(), "burn time of " + fuel);
		}
		int logs = 0;
		for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.LOGS_THAT_BURN)) {
			logs++;
			h.assertValueEqual(level.fuelValues().burnDuration(new ItemStack(holder.value())), FuelTable.LOG_HEAT, "burn time of " + holder.value());
		}
		h.assertTrue(logs > 10, "the log tag has members: " + logs);
		h.assertValueEqual(0, level.fuelValues().burnDuration(new ItemStack(Items.CRIMSON_STEM)), "a crimson stem has no burn time (it is no log that burns)");
		h.assertTrue(dev.villageheart.core.cook.CookRecipes.smokerInputs().equals(dev.villageheart.core.Balance.FOOD_RAW), "raw list");
		VillageTestSupport.done(h);
	}

	// ---- T-L2-10 edge cases ---------------------------------------------------------------------------

	/** Beetroot soup: 6 beetroot + a bowl; 12 beetroot and one bowl make one soup. Soup does not stack: a big batch is at most 5. */
	@GameTest(environment = "villageheart-gametest:cook_soup", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void beetrootSoup(GameTestHelper h) {
		scene(h).thenExecute(() -> {
			putStore(h, Items.BEETROOT, 60);
			putStore(h, Items.BOWL, 10);
			putStore(h, Items.COAL, 2);
			setDayTime(h, 1000);
			hire(h);
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.BEETROOT_SOUP) == 10, "soups " + count(h, Items.BEETROOT_SOUP)))
				.thenExecute(() -> {
					h.assertTrue(count(h, Items.BEETROOT) == 0 && count(h, Items.BOWL) == 0, "all inputs used");
					h.assertTrue(itemEntities(h) == 0, "no item entity was ever left in the world");
					finish(h);
				});
	}

	/** A renamed beef is a player's item: never taken. */
	@GameTest(environment = "villageheart-gametest:cook_renamed", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void renamedFoodIsLeftAlone(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			ItemStack named = new ItemStack(Items.BEEF, 4);
			named.set(DataComponents.CUSTOM_NAME, Component.literal("Bessie"));
			var box = (net.minecraft.world.Container) h.getLevel().getBlockEntity(h.absolutePos(storeChest1()));
			box.setItem(0, named);
			putStore(h, Items.COAL, 2);
			setDayTime(h, 1000);
			cook[0] = hire(h);
		}).thenIdle(600)
				.thenExecute(() -> {
					h.assertTrue(status(h, cook[0]) == WorkerStatus.NO_WORK, "status " + status(h, cook[0]));
					h.assertTrue(count(h, Items.BEEF) == 4 && count(h, Items.COOKED_BEEF) == 0, "the named beef is untouched");
					finish(h);
				});
	}

	// ---- T-L2-12 the smoker's light -------------------------------------------------------------------

	/** A cook that is fired mid-batch does not leave the smoker lit; a smoker a player burns in by hand is left alone. */
	@GameTest(environment = "villageheart-gametest:cook_light", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void smokerLightCleanup(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			setDayTime(h, 1000);
			cook[0] = hire(h, BuildingTestSupport.inside(KITCHEN, 1, 0, 0));
			cook[0].getInventory().addItem(new ItemStack(Items.BEEF, 8));
			cook[0].getInventory().addItem(new ItemStack(Items.COAL, 1));
		}).thenWaitUntil(() -> h.assertTrue(lit(h), "lit while cooking"))
				.thenExecute(() -> dev.villageheart.worker.Workers.fire(h.getLevel(), cook[0]))
				.thenWaitUntil(() -> h.assertFalse(lit(h), "the sweep put the smoker out"))
				.thenExecute(() -> {
					// a player's fire: raw beef and coal in the smoker's own slots
					var smoker = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(smoker()));
					smoker.setItem(0, new ItemStack(Items.BEEF, 1));
					smoker.setItem(1, new ItemStack(Items.COAL, 1));
				})
				.thenWaitUntil(() -> h.assertTrue(lit(h), "vanilla lights its own fire"))
				.thenIdle(250)
				.thenExecute(() -> {
					h.assertTrue(lit(h), "the sweep leaves a burning smoker alone");
					finish(h);
				});
	}

	// ---- needs: a cook that is unpaid or hungry (reviewer r1) -------------------------------------------

	/** An unpaid cook is on strike: it fetches and cooks nothing and holds no reservation; once the treasury pays it, it cooks. */
	@GameTest(environment = "villageheart-gametest:cook_strike", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void strikingCookMakesNothing(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		boolean[] watch = new boolean[1];
		long[] t0 = new long[1];
		List<String> problems = new ArrayList<>();
		h.onEachTick(() -> {
			if (!watch[0] || cook[0] == null || (h.getTick() - t0[0]) % 20 != 0) return;
			if (count(h, Items.COOKED_BEEF) != 0 || carried(cook[0], Items.COOKED_BEEF) != 0) problems.add("@" + h.getTick() + " cooked");
			if (count(h, Items.BEEF) != 8 || carried(cook[0], Items.BEEF) != 0) problems.add("@" + h.getTick() + " beef moved");
			if (reservedBy(h, cook[0].getUUID(), Items.BEEF) != 0 || reservedBy(h, cook[0].getUUID(), Items.COAL) != 0) problems.add("@" + h.getTick() + " a reservation");
			if (lit(h)) problems.add("@" + h.getTick() + " lit");
			if (status(h, cook[0]) != WorkerStatus.ON_STRIKE) problems.add("@" + h.getTick() + " status " + status(h, cook[0]));
		});
		scene(h).thenExecute(() -> {
			NeedsTestHooks.enableWages(); // the hire is charged: treasury 0, so the cook is a striker from its first tick
			putStore(h, Items.BEEF, 8);
			putStore(h, Items.COAL, 1);
			setDayTime(h, 1000);
			cook[0] = hire(h);
			t0[0] = h.getTick();
			watch[0] = true;
		}).thenIdle(1200).thenExecute(() -> {
			watch[0] = false;
			h.assertTrue(problems.isEmpty(), "the striking cook did something: " + problems);
			WageTestSupport.deposit(h, 1);
			t0[0] = h.getTick();
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) + carried(cook[0], Items.COOKED_BEEF) >= 1, "once paid it cooks"))
				.thenExecute(() -> {
					h.assertTrue(h.getTick() - t0[0] <= cookBound(1, 1), "the first item after the pay within the bound: " + (h.getTick() - t0[0]));
					finish(h);
				});
	}

	/** A hungry, unhoused cook (x0.5 x 0.75) makes an item every 333 ticks, not 125. */
	@GameTest(environment = "villageheart-gametest:cook_hungry", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void hungryCookIsSlower(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			NeedsTestHooks.enable();
			setDayTime(h, 1000);
			cook[0] = hire(h, BuildingTestSupport.inside(KITCHEN, 1, 0, 0));
			dev.villageheart.food.DietAttachments.write(cook[0], new dev.villageheart.core.needs.Diet(
					dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().claimedAtGameTime(), 1, 0, 0));
			dev.villageheart.needs.NeedsSystem.mirror(h.getLevel().getServer(), dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow());
			cook[0].getInventory().addItem(new ItemStack(Items.BEEF, 4));
			cook[0].getInventory().addItem(new ItemStack(Items.COAL, 1));
			double speed = dev.villageheart.worker.WorkerAttachments.get(cook[0]).orElseThrow().needs().speed();
			h.assertTrue(Math.abs(speed - 0.375) < 1e-9, "hungry and unhoused: speed " + speed);
		}).thenWaitUntil(() -> h.assertTrue(unitsOf(h, cook[0]).size() >= 4, "units " + unitsOf(h, cook[0]).size()))
				.thenExecute(() -> {
					List<Long> t = unitsOf(h, cook[0]);
					for (int i = 1; i < 4; i++) {
						long gap = t.get(i) - t.get(i - 1);
						h.assertTrue(Math.abs(gap - 333.33) <= 1.5, "gap " + i + " is " + gap + ", expected 333 (125 / 0.375)");
					}
					log("hungryCookIsSlower: gaps " + t);
					finish(h);
				});
	}

	// ---- T-L2-5 two cooks -------------------------------------------------------------------------------

	/** Two cooks planning together never double-claim: reservations stay within the stock, nothing is lost, and the heat is conserved. */
	@GameTest(environment = "villageheart-gametest:cook_two", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void twoCooksShareReservations(GameTestHelper h) {
		Villager[] cooks = new Villager[2];
		boolean[] watch = new boolean[1];
		long[] maxReserved = new long[1];
		List<String> problems = new ArrayList<>();
		// per-tick sampler (a failed assertion in thenWaitUntil would be swallowed): findings are recorded and asserted afterwards
		h.onEachTick(() -> {
			if (!watch[0] || cooks[0] == null || cooks[1] == null) return;
			var key = dev.villageheart.stock.StackKey.of(Items.BEEF);
			var res = Stockpiles.reservations(h.getLevel().getServer());
			long claimed = res.reservedBy(cooks[0].getUUID(), key) + res.reservedBy(cooks[1].getUUID(), key);
			maxReserved[0] = Math.max(maxReserved[0], claimed);
			long total = count(h, Items.BEEF) + count(h, Items.COOKED_BEEF) + carried(cooks[0], Items.BEEF) + carried(cooks[0], Items.COOKED_BEEF)
					+ carried(cooks[1], Items.BEEF) + carried(cooks[1], Items.COOKED_BEEF);
			if (total != 12) problems.add("@" + h.getTick() + " accounted " + total);
			if (claimed > 12) problems.add("@" + h.getTick() + " claimed " + claimed + " of 12");
			if (claimed > count(h, Items.BEEF)) problems.add("@" + h.getTick() + " claimed " + claimed + " of " + count(h, Items.BEEF) + " in stock");
		});
		scene(h).thenExecute(() -> {
			h.setBlock(smoker2(), net.minecraft.world.level.block.Blocks.SMOKER);
			putStore(h, Items.BEEF, 12);
			putStore(h, Items.COAL, 2);
			setDayTime(h, 1000);
		}).thenIdle(60).thenExecute(() -> {
			cooks[0] = hire(h);
			cooks[1] = hire(h, BuildingTestSupport.inside(KITCHEN, 3, 0, 1));
			watch[0] = true;
		}).thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) == 12, "cooked " + count(h, Items.COOKED_BEEF)))
				.thenExecute(() -> {
					watch[0] = false;
					h.assertTrue(problems.isEmpty(), "the two cooks' sampler saw: " + problems);
					h.assertTrue(maxReserved[0] >= 8, "the cooks really reserved their beef: peak claim " + maxReserved[0]);
					h.assertTrue(count(h, Items.COAL) == 0, "coal " + count(h, Items.COAL));
					var village = dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow();
					int banks = village.smokerHeat(BuildingTestSupport.loc(h, smoker())) + village.smokerHeat(BuildingTestSupport.loc(h, smoker2()));
					h.assertTrue(banks == 2 * 1600 - 12 * 200, "the banks sum to " + banks);
					for (Villager c : cooks) {
						var jb = dev.villageheart.worker.WorkerAttachments.get(c).orElseThrow().jobBlock();
						int n = 0;
						for (var u : CookRuntime.get(h.getLevel().getServer()).unitLog()) {
							if (!u.cook().equals(c.getUUID())) continue;
							n++;
							h.assertTrue(u.at().getX() == jb.x() && u.at().getY() == jb.y() && u.at().getZ() == jb.z(), "a unit of " + c.getUUID() + " at " + u.at() + ", its smoker is " + jb);
						}
						h.assertTrue(n > 0, "each cook cooked something: " + n);
					}
					h.assertTrue(!dev.villageheart.worker.WorkerAttachments.get(cooks[0]).orElseThrow().jobBlock().equals(dev.villageheart.worker.WorkerAttachments.get(cooks[1]).orElseThrow().jobBlock()), "two smokers, two cooks");
					finish(h);
				});
	}

	// ---- T-L2-6 the dinner cutoff -------------------------------------------------------------------------

	/** Late in the day only the batch the time allows is taken; nothing is left in the cook's pockets when dinner comes. */
	@GameTest(environment = "villageheart-gametest:cook_dinner", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void foodReachesStorageBeforeDinner(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			putStore(h, Items.BEEF, 8);
			putStore(h, Items.COAL, 1);
			setDayTime(h, 9800);
			cook[0] = hire(h);
		}).thenWaitUntil(() -> {
			long t = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			h.assertTrue(t >= 10990 && t < 11500, "waiting for the end of the work day, at " + t);
		}).thenExecute(() -> {
			h.assertTrue(count(h, Items.COOKED_BEEF) >= 1 && count(h, Items.COOKED_BEEF) <= 3, "cooked " + count(h, Items.COOKED_BEEF));
			h.assertTrue(carried(cook[0], Items.COOKED_BEEF) == 0 && carried(cook[0], Items.BEEF) == 0 && carried(cook[0], Items.COAL) == 0,
					"the cook carries no food: " + cook[0].getInventory());
			h.assertTrue(count(h, Items.BEEF) + count(h, Items.COOKED_BEEF) == 8, "all of it is in Storage");
			h.assertTrue(count(h, Items.BEEF) >= 5, "only the batch the time allows was taken: raw left " + count(h, Items.BEEF));
			finish(h);
		});
	}

	// ---- T-L2-7 a restart mid batch -----------------------------------------------------------------------

	/** The cook's transient state is dropped mid-batch (as after a restart): the inventory is the state, nothing is duplicated or lost, the bank is not charged twice. */
	@GameTest(environment = "villageheart-gametest:cook_reload", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void restartMidBatchResumes(GameTestHelper h) {
		Villager[] cook = new Villager[1];
		scene(h).thenExecute(() -> {
			putStore(h, Items.BEEF, 8);
			putStore(h, Items.COAL, 1);
			setDayTime(h, 1000);
			cook[0] = hire(h);
		}).thenWaitUntil(() -> h.assertTrue(unitsOf(h, cook[0]).size() >= 3, "3 units done: " + unitsOf(h, cook[0]).size()))
				.thenExecute(() -> {
					h.assertTrue(bank(h) == 1600 - 3 * 200, "bank before " + bank(h));
					CookRuntime.resetForTests();
				})
				.thenWaitUntil(() -> h.assertTrue(count(h, Items.COOKED_BEEF) == 8, "cooked " + count(h, Items.COOKED_BEEF)))
				.thenExecute(() -> {
					h.assertTrue(count(h, Items.BEEF) == 0 && count(h, Items.COAL) == 0, "all used");
					h.assertTrue(bank(h) == 0, "no fuel loaded twice, none lost: bank " + bank(h));
					finish(h);
				});
	}
}
