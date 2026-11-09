package dev.villageheart.gametest;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * Helpers for the cook GameTests (M13). Rules (docs/TESTING.md, "Cook GameTests"): one environment per test ({@code cook_<name>}), the
 * {@code yard} structure with {@code skyAccess}, {@code BuildingTestSupport.begin} first and {@code VillageTestSupport.done} last,
 * work tests start at day time 1000, exact counts and measured time bounds, and every roadmap test logs its ticks ({@code [m13] ...}).
 */
public final class CookTestSupport {
	private CookTestSupport() {}

	public static final BlockPos KITCHEN = WorkerTestSupport.ORIGIN_A;
	public static final BlockPos STORE = WorkerTestSupport.ORIGIN_B;

	public static void log(String s) { System.out.println("[m13] " + s); }

	public static BlockPos kitchenMarker() { return BuildingTestSupport.markerPos(KITCHEN, 5); }

	public static BlockPos storeMarker() { return BuildingTestSupport.markerPos(STORE, 5); }

	public static BlockPos smoker() { return BuildingTestSupport.inside(KITCHEN, 0, 0, 0); }

	public static BlockPos table() { return BuildingTestSupport.inside(KITCHEN, 4, 0, 0); }

	public static BlockPos kitchenChest() { return BuildingTestSupport.inside(KITCHEN, 4, 0, 3); }

	public static BlockPos spawnSpot() { return BuildingTestSupport.inside(KITCHEN, 1, 0, 1); }

	public static BlockPos storeChest1() { return BuildingTestSupport.inside(STORE, 0, 0, 3); }

	public static BlockPos storeChest2() { return BuildingTestSupport.inside(STORE, 4, 0, 3); }

	/** Bound of a cooking run: 125 ticks per item, a round trip to the Storage (8 blocks away) per batch, and slack. */
	public static int cookBound(int items, int batches) { return items * 125 + batches * (2 * 10 * 8 + 200) + 400; }

	public static void buildKitchen(GameTestHelper h) {
		BuildingTestSupport.room(h, KITCHEN, 5, 4);
		h.setBlock(smoker(), Blocks.SMOKER);
		h.setBlock(table(), Blocks.CRAFTING_TABLE);
		h.setBlock(kitchenChest(), Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(KITCHEN, 2, 0, 3), Blocks.TORCH);
	}

	public static void buildStorage(GameTestHelper h) {
		BuildingTestSupport.room(h, STORE, 5, 4);
		h.setBlock(storeChest1(), Blocks.CHEST);
		h.setBlock(storeChest2(), Blocks.CHEST);
	}

	public static void assertValid(GameTestHelper h) {
		BuildingTestSupport.assertStatus(h, kitchenMarker(), dev.villageheart.block.MarkerStatus.VALID);
		BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID);
	}

	/** Claim, sweep, grass, a valid Kitchen and Storage; ends when both are valid. */
	public static GameTestSequence scene(GameTestHelper h) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			LumberTestSupport.sweepStrayTrees(h, 40);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			buildKitchen(h);
			buildStorage(h);
			BuildingTestSupport.placeMarker(h, BuildingType.KITCHEN, kitchenMarker(), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker(), Direction.NORTH);
		}).thenWaitUntil(() -> assertValid(h));
	}

	/** Puts {@code n} of {@code item} into the first container of {@code chests} that has room (Storage chests, stack by stack). */
	public static void put(GameTestHelper h, Item item, int n, BlockPos... chests) {
		int left = n;
		for (BlockPos c : chests) {
			Container box = (Container) h.getLevel().getBlockEntity(h.absolutePos(c));
			for (int i = 0; i < box.getContainerSize() && left > 0; i++) {
				ItemStack cur = box.getItem(i);
				if (!cur.isEmpty() && !(cur.is(item) && cur.getComponentsPatch().isEmpty())) continue;
				int room = (cur.isEmpty() ? item.getDefaultMaxStackSize() : cur.getMaxStackSize() - cur.getCount());
				int put = Math.min(room, left);
				if (put <= 0) continue;
				if (cur.isEmpty()) box.setItem(i, new ItemStack(item, put)); else cur.grow(put);
				left -= put;
			}
			box.setChanged();
		}
		h.assertTrue(left == 0, "the chests had room for " + n + " of " + item);
	}

	public static void putStore(GameTestHelper h, Item item, int n) { put(h, item, n, storeChest1(), storeChest2()); }

	public static long count(GameTestHelper h, Item item) { return Stockpiles.count(h.getLevel().getServer(), item); }

	public static int carried(Villager v, Item item) { return v.getInventory().countItem(item); }

	public static int bank(GameTestHelper h) {
		return VillageClaims.current(h.getLevel().getServer()).map(s -> s.smokerHeat(BuildingTestSupport.loc(h, smoker()))).orElse(0);
	}

	public static void setBank(GameTestHelper h, int heat) {
		var loc = BuildingTestSupport.loc(h, smoker());
		VillageClaims.data(h.getLevel().getServer()).update(s -> s.withSmokerHeat(loc, heat));
	}

	public static boolean lit(GameTestHelper h) { return h.getBlockState(smoker()).getValue(AbstractFurnaceBlock.LIT); }

	public static Villager hire(GameTestHelper h) { return hire(h, spawnSpot()); }

	public static Villager hire(GameTestHelper h, BlockPos spawnRel) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, JobType.COOK, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the cook was hired: " + r);
		return v;
	}

	public static dev.villageheart.core.worker.WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	public static void setDayTime(GameTestHelper h, long t) { h.getLevel().setDayTime(t); }

	public static long reservedBy(GameTestHelper h, UUID who, Item item) {
		return Stockpiles.reservations(h.getLevel().getServer()).reservedBy(who, StackKey.of(item));
	}

	/** Every item entity within the structure (a cook never drops food into the world). */
	public static int itemEntities(GameTestHelper h) {
		return h.getEntities(net.minecraft.world.entity.EntityType.ITEM).size();
	}
}
