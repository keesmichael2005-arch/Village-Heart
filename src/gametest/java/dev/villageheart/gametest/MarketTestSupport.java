package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the market GameTests (M22). Rules (docs/TESTING.md, "Market GameTests"): own {@code market_<name>} environment, the {@code yard}
 * structure with {@code skyAccess}, {@link WorkerTestSupport#begin} first, the scene (tier VILLAGE: a Market is a village-tier building), wages
 * off, {@code VillageTestSupport.done} and {@code WorkerTestSupport.end} last. The scene is a Market (stalls on the floor, a chest) next to a
 * Storage (two chests).
 */
public final class MarketTestSupport {
	private MarketTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:market_" + name; }

	public static final BlockPos MARKET = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;

	public static BlockPos marketMarker() { return BuildingTestSupport.markerPos(MARKET, 5); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	public static BlockPos stall(int i) { return BuildingTestSupport.inside(MARKET, i == 0 ? 0 : 4, 0, 0); }

	public static BlockPos marketChest() { return BuildingTestSupport.inside(MARKET, 4, 0, 3); }

	public static BlockPos chest1() { return BuildingTestSupport.inside(STORE, 0, 0, 3); }

	public static BlockPos chest2() { return BuildingTestSupport.inside(STORE, 4, 0, 3); }

	public static BlockPos spawn(int i) { return BuildingTestSupport.inside(MARKET, 1 + i, 0, 1); }

	/** Claim, tier VILLAGE, a Market with {@code stalls} stalls and a Storage, both valid. */
	public static GameTestSequence scene(GameTestHelper h, int stalls) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			NeedsTestSupport.sweepVillagers(h);
			LumberTestSupport.sweepStrayTrees(h, 48);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			BuildingTestSupport.room(h, MARKET, 5, 4);
			for (int i = 0; i < stalls; i++) h.setBlock(stall(i), JobBlocks.BLOCKS.get(JobBlockType.MARKET_STALL));
			h.setBlock(marketChest(), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(MARKET, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.room(h, STORE, 5, 4);
			h.setBlock(chest1(), Blocks.CHEST);
			h.setBlock(chest2(), Blocks.CHEST);
			for (int x = 0; x <= 31; x += 4) for (int z = 0; z <= 31; z += 4) {
				if (!(x >= 7 && x <= 26 && z >= 2 && z <= 10)) h.setBlock(new BlockPos(x, 0, z), Blocks.TORCH);
			}
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.VILLAGE);
			BuildingTestSupport.placeMarker(h, BuildingType.MARKET, marketMarker(), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(), Direction.NORTH);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marketMarker(), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
		});
	}

	public static Villager hire(GameTestHelper h, int spawnIndex) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawn(spawnIndex));
		HireResult r = Workers.hire(h.getLevel(), v, JobType.MERCHANT, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the merchant was hired: " + r);
		return v;
	}

	/** Puts {@code total} of {@code item} into the Storage chests, 64 to a slot, chest 1 first. */
	public static void stock(GameTestHelper h, Item item, int total) {
		int slot = 0;
		while (total > 0) {
			int n = Math.min(64, total);
			BlockPos chest = slot < 27 ? chest1() : chest2();
			LumberTestSupport.putInChest(h, chest, slot % 27, new ItemStack(item, n));
			total -= n;
			slot++;
		}
	}

	/** Puts a stack in the first free slot of the Storage chests. */
	public static void put(GameTestHelper h, ItemStack stack) {
		for (BlockPos chest : List.of(chest1(), chest2())) {
			var c = StockpileTestSupport.container(h, chest);
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).isEmpty()) {
					c.setItem(i, stack);
					c.setChanged();
					return;
				}
			}
		}
		throw new AssertionError("the Storage chests are full");
	}

	public static long count(GameTestHelper h, Item item) { return Stockpiles.count(h.getLevel().getServer(), item); }

	public static long count(GameTestHelper h, StackKey key) { return Stockpiles.count(h.getLevel().getServer(), key); }

	public static int treasury(GameTestHelper h) { return VillageClaims.current(h.getLevel().getServer()).orElseThrow().treasury(); }

	public static void setTreasury(GameTestHelper h, int n) { dev.villageheart.economy.Treasury.set(h.getLevel().getServer(), n); }

	public static WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	public static dev.villageheart.core.worker.WorkerData data(Villager v) { return v.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER); }

	public static long dayTime(GameTestHelper h) { return h.getLevel().getDayTime(); }

	public static long itemEntities(GameTestHelper h, Item item) {
		return h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(12), e -> e.getItem().is(item)).stream().mapToLong(e -> e.getItem().getCount()).sum();
	}

	/** Extra villagers that only count as population (no AI, so they never take a job or wander off). */
	public static List<Villager> population(GameTestHelper h, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Villager v = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(STORE, i, 0, 0));
			v.setNoAi(true);
			out.add(v);
		}
		return out;
	}

	/**
	 * Records on every tick (register {@code h.onEachTick(() -> s.sample(h))} from the test body, never inside a callback) the minimum count of
	 * each watched item, and whether a conservation law held. Violations are collected and asserted at the end.
	 */
	public static final class Sampler {
		private boolean on;
		public final Map<Item, Long> min = new LinkedHashMap<>();
		public long minFoodPool = Long.MAX_VALUE;
		public final List<String> violations = new ArrayList<>();
		private final List<Item> watched;
		private LongSupplier conserved;
		private long expected;

		public Sampler(Item... items) { watched = List.of(items); }

		public void start() { on = true; }

		public void stop() { on = false; }

		/** Law: {@code conserved.getAsLong() == expected} on every tick. */
		public void conserve(long expected, LongSupplier total) {
			this.expected = expected;
			this.conserved = total;
		}

		public void sample(GameTestHelper h) {
			if (!on) return;
			for (Item i : watched) min.merge(i, count(h, i), Math::min);
			if (conserved != null) {
				long now = conserved.getAsLong();
				if (now != expected && violations.size() < 5) violations.add("tick " + h.getTick() + ": " + now + " != " + expected);
			}
		}
	}
}
