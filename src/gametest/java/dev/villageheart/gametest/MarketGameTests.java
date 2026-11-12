package dev.villageheart.gametest;

import static dev.villageheart.gametest.MarketTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.market.PriceEntry;
import dev.villageheart.core.market.PriceTable;
import dev.villageheart.core.market.Reserves;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.market.Markets;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatusText;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: the merchant and the market (M22). Rules (docs/TESTING.md, "Market GameTests"): own {@code market_<name>} environment, the
 * {@code yard} structure with {@code skyAccess}, {@code MarketTestSupport.scene} first, {@code VillageTestSupport.done} +
 * {@code WorkerTestSupport.end} last. Literals in assertions, never values computed from the price table.
 */
public class MarketGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static void finish(GameTestHelper h) {
		VillageTestSupport.done(h);
		end(h);
	}

	private static void log(String s) { System.out.println("[m22] " + s); }

	private static long soldBy(GameTestHelper h, Villager v) { return Markets.ledgerToday(h.getLevel()).soldBy(v.getUUID()); }

	private static void toNextMorning(GameTestHelper h) {
		long day = Math.floorDiv(h.getLevel().getDayTime(), 24000L);
		h.getLevel().setDayTime((day + 1) * 24000L + 1000);
	}

	// ---- T-L2-1 (roadmap) -------------------------------------------------------------------------

	/** 200 cobblestone and a merchant: 4 emeralds within the working day, and the stock never goes below the reserve of 64. */
	@GameTest(environment = "villageheart-gametest:market_roadmap", structure = YARD, skyAccess = true, maxTicks = 12500)
	public void twoHundredCobblestoneFourEmeralds(GameTestHelper h) {
		begin(h);
		Sampler s = new Sampler(Items.COBBLESTONE);
		h.onEachTick(() -> s.sample(h));
		Villager[] v = new Villager[1];
		long[] views = new long[3];
		long[] firstSale = {-1};
		long[] xp0 = new long[1], sales0 = new long[1], em0 = new long[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			h.assertValueEqual(32, PriceTable.lot("minecraft:cobblestone").orElse(-1), "the cobblestone lot");
			stock(h, Items.COBBLESTONE, 200);
			h.getLevel().setDayTime(1000);
			sales0[0] = WorkStats.get("merchant", "sales");
			em0[0] = WorkStats.get("merchant", "emeralds");
			v[0] = hire(h, 0);
			s.conserve(200, () -> count(h, Items.COBBLESTONE) + 32L * treasury(h) + itemEntities(h, Items.COBBLESTONE));
			s.start();
		});
		seq = seq.thenWaitUntil(() -> {
			if (treasury(h) >= 1 && firstSale[0] < 0) firstSale[0] = h.getTick();
			h.assertTrue(treasury(h) >= 4, "treasury " + treasury(h));
		}).thenExecute(() -> {
			h.assertTrue(dayTime(h) < 11000, "the 4 emeralds came within one working day: " + dayTime(h));
			views[0] = Markets.freshViews;
			views[1] = dayTime(h);
			log("roadmap: first sale at tick " + firstSale[0] + ", 4th at day time " + dayTime(h));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(dayTime(h) >= 8000, "wait for the middle of the working day"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(WorkerStatus.NO_WORK, status(h, v[0]), "stopped for lack of surplus");
			String words = WorkerStatusText.of(WorkerStatus.NO_WORK, JobType.MERCHANT).getString();
			h.assertTrue(words.contains("Nothing to sell"), "the words: " + words);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(dayTime(h) >= 11000, "wait for the end of the working day"));
		seq = seq.thenExecute(() -> {
			s.stop();
			h.assertValueEqual(4, treasury(h), "emeralds");
			h.assertValueEqual(72L, count(h, Items.COBBLESTONE), "cobblestone left");
			h.assertTrue(s.min.get(Items.COBBLESTONE) >= 64, "never below the reserve: " + s.min);
			h.assertTrue(s.violations.isEmpty(), "conservation: " + s.violations);
			h.assertValueEqual(8, MarketTestSupport.data(v[0]).xp(), "2 XP per sale");
			h.assertValueEqual(4L, WorkStats.get("merchant", "sales") - sales0[0], "work.merchant.sales");
			h.assertValueEqual(4L, WorkStats.get("merchant", "emeralds") - em0[0], "work.merchant.emeralds");
			long grown = Markets.freshViews - views[0];
			long ticks = dayTime(h) - views[1];
			h.assertTrue(grown <= ticks / 20 + 2, "no per-tick re-planning while stopped: " + grown + " views in " + ticks + " ticks");
			h.assertValueEqual(0L, itemEntities(h, Items.COBBLESTONE), "no item on the ground");
			finish(h);
		});
	}

	// ---- T-L2-2 (food reserve) --------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_food", structure = YARD, skyAccess = true, maxTicks = 14000)
	public void foodReserveThreeDays(GameTestHelper h) {
		begin(h);
		Sampler s = new Sampler(Items.COOKED_BEEF, Items.WHEAT);
		h.onEachTick(() -> s.sample(h));
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			population(h, 3);
			stock(h, Items.COOKED_BEEF, 40);
			MarketTestSupport.put(h, new ItemStack(Items.WHEAT, 30));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			var count = VillageCensus.headcount(h.getLevel().getServer(), VillageClaims.current(h.getLevel().getServer()).orElseThrow());
			h.assertValueEqual(4, count.adults(), "adults (3 NoAI + the merchant)");
			var facts = dev.villageheart.heart.HeartFacts.collect(h.getLevel().getServer()).orElseThrow();
			h.assertValueEqual(facts.adults(), count.adults(), "the Overview's adults");
			h.assertValueEqual(facts.babies(), count.babies(), "the Overview's babies");
			s.start();
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 2, "two lots sold: " + treasury(h)));
		seq = seq.thenIdle(1300).thenExecute(() -> {
			h.assertValueEqual(2, treasury(h), "a third lot would leave less than 3 days of food");
			h.assertValueEqual(20L, count(h, Items.COOKED_BEEF), "cooked beef");
			h.assertValueEqual(30L, count(h, Items.WHEAT), "wheat is below the item reserve");
			h.assertTrue(s.min.get(Items.COOKED_BEEF) >= 15, "the pool never below 120 nutrition: " + s.min);
			// the population grows: 3 more adults (demand 70, reserve 210), and 10 more beef in the same tick
			population(h, 3);
			MarketTestSupport.put(h, new ItemStack(Items.COOKED_BEEF, 10));
			h.assertValueEqual(30L, count(h, Items.COOKED_BEEF), "30 beef: pool 240, spare 30");
		});
		seq = seq.thenIdle(1300).thenExecute(() -> {
			s.stop();
			h.assertValueEqual(2, treasury(h), "no sale on the old population");
			h.assertValueEqual(30L, count(h, Items.COOKED_BEEF), "beef kept");
			h.assertValueEqual(WorkerStatus.NO_WORK, status(h, v[0]), "nothing to sell");
			finish(h);
		});
	}

	// ---- T-L2-5 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_reserved", structure = YARD, skyAccess = true, maxTicks = 12500)
	public void reservationsAndVariantsAreNotSold(GameTestHelper h) {
		begin(h);
		UUID fakeBuild = UUID.randomUUID();
		StackKey plain = StackKey.of(Items.COBBLESTONE);
		ItemStack named = new ItemStack(Items.COBBLESTONE, 40);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Mine"));
		StackKey namedKey = StackKey.of(named);
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 200);
			MarketTestSupport.put(h, named.copy());
			Stockpiles.reservations(h.getLevel().getServer()).set(fakeBuild, plain, 100);
			h.getLevel().setDayTime(1000);
			hire(h, 0);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 1, "one sale"));
		seq = seq.thenIdle(1300).thenExecute(() -> {
			h.assertValueEqual(1, treasury(h), "200 - 100 reserved - 64 = 36: one lot");
			h.assertValueEqual(168L, count(h, plain), "plain cobblestone");
			h.assertValueEqual(40L, count(h, namedKey), "the renamed stack is the player's");
			Stockpiles.reservations(h.getLevel().getServer()).release(fakeBuild);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 4, "3 more sales after the release: " + treasury(h)));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(72L, count(h, plain), "plain cobblestone");
			h.assertValueEqual(40L, count(h, namedKey), "the renamed stack");
			finish(h);
		});
	}

	// ---- T-L2-7 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_invalid", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void invalidMarketStops(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 400);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 1, "first sale"));
		seq = seq.thenExecute(() -> h.setBlock(marketChest(), Blocks.AIR));
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marketMarker(), dev.villageheart.block.MarkerStatus.INVALID));
		int[] at = new int[1];
		seq = seq.thenExecute(() -> {
			at[0] = treasury(h);
			h.assertValueEqual(WorkerStatus.WORKPLACE_INVALID, status(h, v[0]), "status");
		});
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertValueEqual(at[0], treasury(h), "no sale from an invalid building");
			h.setBlock(marketChest(), Blocks.CHEST);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marketMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) > at[0], "sales resume"));
		seq.thenExecute(() -> finish(h));
	}

	// ---- T-L2-9 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_tables", structure = YARD, skyAccess = true, maxTicks = 100)
	public void tablesMatchTheGame(GameTestHelper h) {
		VillageTestSupport.reset(h);
		for (PriceEntry e : PriceTable.entries()) {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(e.itemId())).orElse(null);
			h.assertTrue(item != null, "the item exists: " + e.itemId());
			h.assertTrue(e.lot() <= item.getDefaultMaxStackSize(), e.itemId() + ": the lot fits one stack");
			boolean food = dev.villageheart.food.FoodCatalog.of(StackKey.of(item)).isPresent();
			boolean expectFood = List.of("carrot", "potato", "beetroot").contains(e.itemId().substring(10)) || e.itemId().startsWith("minecraft:cooked_");
			h.assertTrue(food == expectFood, e.itemId() + ": village food = " + food);
			h.assertTrue(!item.builtInRegistryHolder().is(dev.villageheart.heart.HeartFacts.NOT_VILLAGE_FOOD), e.itemId() + " is not in not_village_food");
			if (e.itemId().endsWith("_log")) {
				h.assertTrue(item.builtInRegistryHolder().is(ItemTags.LOGS), e.itemId() + " is a log item");
				var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(e.itemId()));
				h.assertTrue(block.defaultBlockState().is(BlockTags.OVERWORLD_NATURAL_LOGS), e.itemId() + " is a natural log");
			}
		}
		VillageTestSupport.done(h);
		h.succeed();
	}

	// ---- T-L2-10 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_trades", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void unemployedKeepVanillaTrades(GameTestHelper h) {
		begin(h);
		Villager[] lib = new Villager[1], v = new Villager[1];
		List<String> before = new ArrayList<>();
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			lib[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(STORE, 2, 0, 0));
			lib[0].setVillagerData(lib[0].getVillagerData().withProfession(h.getLevel().registryAccess()
					.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).getOrThrow(net.minecraft.world.entity.npc.villager.VillagerProfession.LIBRARIAN)).withLevel(2));
			lib[0].setNoAi(true);
			lib[0].getOffers();
			h.assertTrue(!lib[0].getOffers().isEmpty(), "the librarian has offers");
			lib[0].getOffers().forEach(o -> before.add(sig(o)));
			stock(h, Items.COBBLESTONE, 200);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 2, "sales happened"));
		seq.thenExecute(() -> {
			List<String> after = new ArrayList<>();
			lib[0].getOffers().forEach(o -> after.add(sig(o)));
			h.assertValueEqual(before, after, "vanilla offers (cost A, cost B, result)");
			h.assertTrue(v[0].getOffers().isEmpty(), "the hired merchant has no trades");
			finish(h);
		});
	}

	private static String sig(net.minecraft.world.item.trading.MerchantOffer o) {
		return o.getBaseCostA() + "|" + o.getCostB() + "|" + o.getResult();
	}

	// ---- T-L2-11 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_two", structure = YARD, skyAccess = true, maxTicks = 12500)
	public void twoMerchantsNeverOversell(GameTestHelper h) {
		begin(h);
		Sampler s = new Sampler(Items.COBBLESTONE);
		h.onEachTick(() -> s.sample(h));
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 200);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			v[1] = hire(h, 2);
			s.conserve(200, () -> count(h, Items.COBBLESTONE) + 32L * treasury(h));
			s.start();
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 4, "4 emeralds"));
		seq = seq.thenWaitUntil(() -> h.assertTrue(dayTime(h) >= 11000, "end of the working day"));
		seq.thenExecute(() -> {
			s.stop();
			h.assertValueEqual(4, treasury(h), "emeralds in total");
			h.assertValueEqual(72L, count(h, Items.COBBLESTONE), "cobblestone");
			h.assertTrue(s.min.get(Items.COBBLESTONE) >= 64, "never below the reserve: " + s.min);
			h.assertTrue(s.violations.isEmpty(), "conservation: " + s.violations);
			h.assertTrue(soldBy(h, v[0]) >= 1 && soldBy(h, v[1]) >= 1, "each made a sale: " + soldBy(h, v[0]) + ", " + soldBy(h, v[1]));
			h.assertValueEqual(4L, soldBy(h, v[0]) + soldBy(h, v[1]), "sum");
			finish(h);
		});
	}

	// ---- T-L2-13 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_hours", structure = YARD, skyAccess = true, maxTicks = 16000)
	public void sellsOnlyAtTheStallInWorkHours(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		List<BlockPos> fences = new ArrayList<>();
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 400);
			// the merchant starts in a pen of fences on the grass, 8 blocks south of the Market
			for (int x = 19; x <= 21; x++) for (int z = 18; z <= 20; z++) {
				boolean edge = x == 19 || x == 21 || z == 18 || z == 20;
				if (edge) {
					BlockPos p = new BlockPos(x, 1, z);
					h.setBlock(p, Blocks.OAK_FENCE);
					fences.add(p);
				}
			}
			h.getLevel().setDayTime(1000);
			v[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(20, 1, 19));
			var r = dev.villageheart.worker.Workers.hire(h.getLevel(), v[0], JobType.MERCHANT, java.util.Optional.empty());
			h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "hired " + r);
		});
		seq = seq.thenIdle(1800).thenExecute(() -> {
			h.assertValueEqual(0, treasury(h), "no sale from the pen");
			h.assertTrue(WorkerTestSupport.distance(h, v[0], stall(0)) > 3, "still penned in");
			h.assertValueEqual(WorkerStatus.SITE_UNREACHABLE, status(h, v[0]), "status");
			String words = WorkerStatusText.of(WorkerStatus.SITE_UNREACHABLE, JobType.MERCHANT).getString();
			h.assertTrue(words.contains("stall"), "the words: " + words);
			for (BlockPos p : fences) h.setBlock(p, Blocks.AIR);
			h.getLevel().setDayTime(12500);
		});
		int[] at = new int[1];
		seq = seq.thenIdle(5).thenExecute(() -> at[0] = treasury(h));
		seq = seq.thenWaitUntil(() -> h.assertTrue(dayTime(h) >= 23000 || dayTime(h) < 1000, "night passes")).thenExecute(() -> {
			h.assertValueEqual(at[0], treasury(h), "no sale at night");
			toNextMorning(h);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) > at[0], "sales start in the morning"));
		seq.thenExecute(() -> finish(h));
	}

	// ---- T-L2-14 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_short", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void shortWithdrawLosesNothing(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		long[] calls = new long[2];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 200);
			Markets.withdrawLimitForTest = lot -> 20;
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			s.conserve(200, () -> count(h, Items.COBBLESTONE) + invCount(v[0]) + itemEntities(h, Items.COBBLESTONE) + 32L * treasury(h));
			s.start();
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(Markets.withdrawCalls > 0, "a sale was attempted"));
		seq = seq.thenExecute(() -> calls[0] = Markets.withdrawCalls);
		seq = seq.thenIdle(600).thenExecute(() -> {
			h.assertValueEqual(0, treasury(h), "no sale");
			h.assertValueEqual(WorkerStatus.STORAGE_UNREACHABLE, status(h, v[0]), "status");
			h.assertTrue(Markets.withdrawCalls - calls[0] <= 600 / 20 + 2, "at most one withdraw per 20 ticks: " + (Markets.withdrawCalls - calls[0]));
			h.assertTrue(s.violations.isEmpty(), "conservation: " + s.violations);
			Markets.withdrawLimitForTest = null;
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 4, "sales resume"));
		seq.thenExecute(() -> {
			s.stop();
			h.assertTrue(s.violations.isEmpty(), "conservation: " + s.violations);
			h.assertValueEqual(72L, count(h, Items.COBBLESTONE) + invCount(v[0]), "cobblestone ends at 72");
			finish(h);
		});
	}

	private static long invCount(Villager v) {
		long n = 0;
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.COBBLESTONE)) n += inv.getItem(i).getCount();
		return n;
	}

	// ---- T-L2-3 (daily cap by level) ---------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_cap", structure = YARD, skyAccess = true, maxTicks = 30000)
	public void dailyCapByLevel(GameTestHelper h) {
		// M26: the caps are Balance values (tuned in M26 H1), not 8 and 12
		final int C1 = dev.villageheart.core.Balance.merchantDailyCap(1), C2 = dev.villageheart.core.Balance.merchantDailyCap(2);
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 2000);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			LevelTestSupport.put(v[0], 1, 0);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(soldBy(h, v[0]) >= C1, C1 + " sales: " + soldBy(h, v[0])));
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertValueEqual((long) C1, soldBy(h, v[0]), "level 1 stops at its cap");
			h.assertValueEqual(C1, treasury(h), "emeralds");
			h.assertValueEqual(WorkerStatus.DAILY_LIMIT, status(h, v[0]), "status");
			h.assertTrue(MarketTestSupport.data(v[0]).level() == 1, "still level 1");
			// a new day at level 2
			LevelTestSupport.put(v[0], 2, MarketTestSupport.data(v[0]).xp());
			toNextMorning(h);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(soldBy(h, v[0]) >= C2, C2 + " sales on the new day: " + soldBy(h, v[0])));
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertValueEqual((long) C2, soldBy(h, v[0]), "level 2 stops at its cap");
			h.assertValueEqual(C1 + C2, treasury(h), "emeralds");
			h.assertValueEqual(C2, Markets.ledgerToday(h.getLevel()).soldAt(dev.villageheart.gametest.WorkerTestSupport.loc(h, stall(0))), "the stall's count");
			h.assertValueEqual(WorkerStatus.DAILY_LIMIT, status(h, v[0]), "status");
			finish(h);
		});
	}

	// ---- T-L2-8 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_reload", structure = YARD, skyAccess = true, maxTicks = 14000)
	public void ledgerSurvivesReload(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		var before = new dev.villageheart.core.market.MarketState[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 2000);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			LevelTestSupport.put(v[0], 1, 0);
			Markets.setReserves(h.getLevel().getServer(), new Reserves(5, 96)); // not Reserves.DEFAULT, so the round trip proves them
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(soldBy(h, v[0]) >= dev.villageheart.core.Balance.merchantDailyCap(1), "cap sales"));
		seq = seq.thenExecute(() -> {
			before[0] = Markets.state(h.getLevel().getServer());
			dev.villageheart.core.market.MarketState read;
			try {
				read = VillageTestSupport.reload(h.getLevel().getServer()).market(); // the copy read back from disk, not the live state
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			h.assertValueEqual(new Reserves(5, 96), read.reserves(), "reserves after a reload");
			h.assertValueEqual(before[0], read, "ledger and reserves after a reload");
		});
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertValueEqual(dev.villageheart.core.Balance.merchantDailyCap(1), treasury(h), "the cap holds after a reload");
			finish(h);
		});
	}

	// ---- T-L2-15 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_village_cap", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void villageCapHolds(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 2000);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			var server = h.getLevel().getServer();
			var ghost = dev.villageheart.core.market.SalesLedger.empty(Markets.today(h.getLevel()))
					.record(UUID.randomUUID(), WorkerTestSupport.loc(h, new BlockPos(30, 1, 30)), 31);
			VillageClaims.data(server).setMarket(Markets.state(server).withLedger(ghost));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(soldBy(h, v[0]) >= 1, "one sale"));
		seq = seq.thenIdle(1300).thenExecute(() -> {
			h.assertValueEqual(1L, soldBy(h, v[0]), "this merchant sold one");
			h.assertValueEqual(32, Markets.ledgerToday(h.getLevel()).soldToday(), "the village cap");
			h.assertValueEqual(WorkerStatus.DAILY_LIMIT, status(h, v[0]), "status");
			finish(h);
		});
	}

	// ---- T-L2-6 -----------------------------------------------------------------------------------

	/** A striking merchant does not sell; the first emerald pays it, and its first sale's emerald pays the next striker in the same tick. */
	@GameTest(environment = "villageheart-gametest:market_strike", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void strikeStopsSalesIncomeSettles(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		dev.villageheart.core.economy.PayResult[] pay = new dev.villageheart.core.economy.PayResult[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			WorkerTestSupport.farmhouse(h, WageTestSupport.FARM, 1);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.FARMHOUSE, WageTestSupport.farmMarker(), net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, WageTestSupport.farmMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 200);
			v[0] = hire(h, 0);
			v[1] = WageTestSupport.hireFarmer(h);
			WageTestSupport.wagesOn();
			setTreasury(h, 0);
		});
		seq = WageTestSupport.toDawn(h, seq, pay);
		seq = seq.thenIdle(1800).thenExecute(() -> {
			h.assertValueEqual(WorkerStatus.ON_STRIKE, status(h, v[0]), "the merchant strikes");
			h.assertValueEqual(0, treasury(h), "treasury");
			h.assertValueEqual(200L, count(h, Items.COBBLESTONE), "no sale while on strike");
			h.assertTrue(WorkerTestSupport.distance(h, v[0], stall(0)) <= 1.6, "at its stall");
			dev.villageheart.economy.Treasury.deposit(h.getLevel().getServer(), 1, null);
			h.assertTrue(WageTestSupport.paid(v[0]), "the first emerald pays the merchant (hired first)");
			h.assertTrue(!WageTestSupport.paid(v[1]), "the farmer still strikes");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(soldBy(h, v[0]) >= 1, "the merchant sells within a sale time"));
		seq.thenExecute(() -> {
			h.assertValueEqual(168L, count(h, Items.COBBLESTONE), "one lot sold");
			h.assertTrue(WageTestSupport.paid(v[1]), "the sale's emerald paid the farmer");
			h.assertTrue(WageTestSupport.payroll(h).unpaid().isEmpty(), "nobody is unpaid");
			h.assertValueEqual(0, treasury(h), "the emerald went to the wage");
			WageTestSupport.wagesOff();
			finish(h);
		});
	}

	// ---- T-L2-12 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:market_full", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void treasuryFullStops(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			stock(h, Items.COBBLESTONE, 200);
			setTreasury(h, dev.villageheart.core.Balance.MAX_TREASURY);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
		});
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertValueEqual(dev.villageheart.core.Balance.MAX_TREASURY, treasury(h), "nothing went into a full treasury");
			h.assertValueEqual(200L, count(h, Items.COBBLESTONE), "the goods stay");
			h.assertValueEqual(WorkerStatus.TREASURY_FULL, status(h, v[0]), "status");
			setTreasury(h, dev.villageheart.core.Balance.MAX_TREASURY - 1);
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(168L, count(h, Items.COBBLESTONE), "one sale"));
		seq = seq.thenIdle(30).thenExecute(() -> {
			h.assertValueEqual(dev.villageheart.core.Balance.MAX_TREASURY, treasury(h), "full again");
			h.assertValueEqual(WorkerStatus.TREASURY_FULL, status(h, v[0]), "status again");
			setTreasury(h, 0);
			finish(h);
		});
	}

	// ---- T-L2-4 -----------------------------------------------------------------------------------

	private static void act(net.minecraft.server.level.ServerPlayer p, dev.villageheart.core.heart.HeartAction a) {
		dev.villageheart.heart.HeartActions.RATE.forget(p.getUUID());
		dev.villageheart.heart.HeartActions.handle(p, p.containerMenu.containerId, a);
	}

	private static dev.villageheart.core.heart.Stock lastStock(List<HeartTestSupport.Sent> sent, net.minecraft.server.level.ServerPlayer p) {
		var all = HeartTestSupport.snapshotsTo(sent, p);
		return all.get(all.size() - 1).snapshot().stock();
	}

	/** A Steward sets the reserves and every open screen shows them; a Resident cannot; a crafted value is refused; they survive a reload. */
	@GameTest(environment = "villageheart-gametest:market_ui", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void reserveSettingsOnTheScreen(GameTestHelper h) {
		begin(h);
		net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[3]; // owner, steward, resident
		List<HeartTestSupport.Sent>[] sent = new List[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			sent[0] = HeartTestSupport.recorder();
			p[0] = BuildingTestSupport.owner();
			p[1] = HeartTestSupport.player(h, "stew", dev.villageheart.core.Rank.STEWARD);
			p[2] = HeartTestSupport.player(h, "res", dev.villageheart.core.Rank.RESIDENT);
			for (var q : p) HeartTestSupport.open(h, q);
			stock(h, Items.COBBLESTONE, 200);
			h.getLevel().setDayTime(1000);
			hire(h, 0);
			act(p[1], new dev.villageheart.core.heart.HeartAction.SetReserves(3, 128));
			h.assertValueEqual(new Reserves(3, 128), Markets.state(h.getLevel().getServer()).reserves(), "the Steward's setting is saved");
		});
		seq = seq.thenIdle(25).thenExecute(() -> {
			for (var q : p) {
				var st = lastStock(sent[0], q);
				h.assertValueEqual(new Reserves(3, 128), st.reserves(), q.getName().getString() + " sees the reserves");
				long cobble = st.items().stream().filter(l -> l.itemId().equals("minecraft:cobblestone")).mapToLong(l -> l.forSale()).sum();
				h.assertValueEqual(72L, cobble, q.getName().getString() + " sees what is for sale");
				h.assertValueEqual(1, st.merchants(), "merchants");
			}
			act(p[2], new dev.villageheart.core.heart.HeartAction.SetReserves(3, 0));
			h.assertValueEqual(dev.villageheart.core.heart.Rejection.NO_PERMISSION.langKey(), HeartTestSupport.lastResult(sent[0], p[2]).key(), "a Resident cannot");
			act(p[0], new dev.villageheart.core.heart.HeartAction.SetReserves(31, 64));
			h.assertValueEqual(dev.villageheart.core.heart.Rejection.BAD_RESERVE.langKey(), HeartTestSupport.lastResult(sent[0], p[0]).key(), "out of range");
			h.assertValueEqual(new Reserves(3, 128), Markets.state(h.getLevel().getServer()).reserves(), "unchanged");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(treasury(h) >= 2, "two sales: " + treasury(h)));
		seq = seq.thenIdle(700).thenExecute(() -> {
			h.assertValueEqual(2, treasury(h), "200 - 128 = 72: two lots");
			h.assertValueEqual(136L, count(h, Items.COBBLESTONE), "cobblestone");
			dev.villageheart.core.market.MarketState read;
			try {
				read = VillageTestSupport.reload(h.getLevel().getServer()).market(); // the copy read back from disk
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			h.assertValueEqual(new Reserves(3, 128), read.reserves(), "reserves after a reload");
			HeartTestSupport.done(h);
		});
	}
}
