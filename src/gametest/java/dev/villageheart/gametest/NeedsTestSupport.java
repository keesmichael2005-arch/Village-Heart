package dev.villageheart.gametest;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.food.DinnerClock;
import dev.villageheart.core.needs.Diet;
import dev.villageheart.food.DietAttachments;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Helpers for the needs and dinner GameTests (M11). Rules (docs/TESTING.md, "Needs GameTests"): own {@code needs_<name>} environment, the
 * {@code yard} structure with {@code skyAccess}, {@link WorkerTestSupport#begin} first, a scene that ends with {@link NeedsTestHooks#enable}
 * (the reset turned needs and dinner off for everybody else), villagers counted before the first dinner, dinners triggered by moving the day
 * time to the NEXT day's 10980 ({@link #nextDinner}), {@code VillageTestSupport.done} last.
 */
public final class NeedsTestSupport {
	private NeedsTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:needs_" + name; }

	public static void log(String s) { System.out.println("[m11] " + s); }

	/** A valid Storage (two chests) and the claim; stray villagers swept; needs and dinner on. Ends when the Storage is valid. */
	public static GameTestSequence storageScene(GameTestHelper h) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			FarmTestSupport.buildStorage(h);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, FarmTestSupport.storeMarker(), net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, FarmTestSupport.storeMarker(), dev.villageheart.block.MarkerStatus.VALID))
				.thenExecute(NeedsTestHooks::enable);
	}

	/** Every villager within 100 blocks goes: other tests' leftovers would eat the test's bread. */
	public static void sweepVillagers(GameTestHelper h) {
		BlockPos c = h.absolutePos(BlockPos.ZERO);
		for (Villager v : h.getLevel().getEntities(EntityType.VILLAGER, new AABB(c).inflate(100), x -> true)) v.discard();
	}

	public static void putBread(GameTestHelper h, int n) { put(h, new ItemStack(net.minecraft.world.item.Items.BREAD, n)); }

	public static void put(GameTestHelper h, ItemStack... stacks) { StockpileTestSupport.fill(h, FarmTestSupport.storeChest1(), stacks); }

	public static long count(GameTestHelper h, Item item) { return Stockpiles.count(h.getLevel().getServer(), item); }

	public static List<Villager> spawn(GameTestHelper h, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) out.add(WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(FarmTestSupport.STORE, i % 5, 0, 1 + i / 5)));
		return out;
	}

	public static VillageState village(GameTestHelper h) { return VillageClaims.current(h.getLevel().getServer()).orElseThrow(); }

	public static int census(GameTestHelper h) { return VillageCensus.of(h.getLevel().getServer(), village(h)).size(); }

	public static Diet diet(GameTestHelper h, Villager v) { return DietAttachments.read(v, village(h).claimedAtGameTime()); }

	/** Today's dinner counts as served; the clock goes to the next day's 10980, so dinner is due 20 ticks later. */
	public static void nextDinner(GameTestHelper h) {
		long now = h.getLevel().getDayTime();
		long today = DinnerClock.dayIndex(now);
		VillageClaims.data(h.getLevel().getServer()).setLastDinnerDay(today);
		h.getLevel().setDayTime((today + 1) * 24000L + 10980);
	}

	public static long lastDinnerDay(GameTestHelper h) { return VillageClaims.data(h.getLevel().getServer()).lastDinnerDay().orElse(Long.MIN_VALUE); }
}
