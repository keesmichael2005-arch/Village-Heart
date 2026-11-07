package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;
import static dev.villageheart.gametest.StockpileTestSupport.*;

import dev.villageheart.block.MarkerStatus;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.sim.TickStats;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.stock.StorageAlert;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.StockpileSystem;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageClaims;
import java.util.Arrays;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Layer 2: the stockpile facade and system on top of the building registry (M6). Building GameTests rules: own environment per
 * test, the {@code yard} structure, {@code skyAccess}, {@link BuildingTestSupport#begin} first and {@code done} last, measured waits.
 */
public class StockpileFacadeGameTests {
	private static final String Y = YARD;
	private static final BlockPos A = new BlockPos(8, 0, 4), B = new BlockPos(18, 0, 4), C = new BlockPos(8, 0, 14);

	private static ItemStack stack(Item item, int n) { return new ItemStack(item, n); }

	private static ItemStack named(Item item, int n, String name) {
		ItemStack s = new ItemStack(item, n);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return s;
	}

	/** A 5x4 Storage room whose two containers stand at the two ends of the south row. Returns the marker position. */
	private static BlockPos storageRoom(GameTestHelper h, BlockPos origin, Block first, Block second) {
		BlockPos marker = room(h, origin, 5, 4);
		h.setBlock(c1(origin), first);
		h.setBlock(c2(origin), second);
		return marker;
	}

	private static BlockPos c1(BlockPos origin) { return inside(origin, 0, 0, 3); }

	private static BlockPos c2(BlockPos origin) { return inside(origin, 4, 0, 3); }

	/** Where a worker would stand just outside the room's door. */
	private static BlockPos outsideDoor(GameTestHelper h, BlockPos origin) {
		return h.absolutePos(new BlockPos(origin.getX() + 3, origin.getY() + 1, origin.getZ() - 1));
	}

	private static void bed(GameTestHelper h, BlockPos foot) {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		h.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT));
		h.setBlock(foot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static MinecraftServer server(GameTestHelper h) { return h.getLevel().getServer(); }

	private static void assertSites(GameTestHelper h, int sites) {
		StockSnapshot<StackKey> s = Stockpiles.snapshot(server(h));
		h.assertValueEqual(sites, s.sites(), "sites with readable containers");
		h.assertValueEqual(0, s.unknownSites(), "no unknown sites");
	}

	// ---- T-L2-1 (roadmap) ------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_totals", structure = Y, skyAccess = true, maxTicks = 500)
	public void totalsAreSumOfStorages(GameTestHelper h) {
		BlockPos ma = markerPos(A, 5), mb = markerPos(B, 5), mc = markerPos(C, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			storageRoom(h, B, Blocks.CHEST, Blocks.BARREL);
			fill(h, c1(A), stack(Items.OAK_LOG, 20), stack(Items.BREAD, 10));
			fill(h, c2(A), stack(Items.COBBLESTONE, 64));
			fill(h, c1(B), stack(Items.OAK_LOG, 5));
			fill(h, c2(B), stack(Items.BREAD, 3));
			// a House with a chest of 99 logs: not part of the stockpile
			room(h, C, 5, 4);
			bed(h, inside(C, 1, 0, 1));
			h.setBlock(inside(C, 3, 0, 1), Blocks.TORCH);
			h.setBlock(c1(C), Blocks.CHEST);
			fill(h, c1(C), stack(Items.OAK_LOG, 64), stack(Items.OAK_LOG, 35));
			placeMarker(h, BuildingType.STORAGE, ma, Direction.NORTH);
			placeMarker(h, BuildingType.STORAGE, mb, Direction.NORTH);
			placeMarker(h, BuildingType.HOUSE, mc, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(3, 0), "both Storages counted", () -> assertSites(h, 2));
		seq.thenExecute(() -> {
			MinecraftServer srv = server(h);
			h.assertValueEqual(25L, Stockpiles.count(srv, Items.OAK_LOG), "logs");
			h.assertValueEqual(13L, Stockpiles.count(srv, Items.BREAD), "bread");
			h.assertValueEqual(64L, Stockpiles.count(srv, Items.COBBLESTONE), "cobblestone");
			h.assertValueEqual(Map.of("minecraft:oak_log", 25L, "minecraft:bread", 13L, "minecraft:cobblestone", 64L),
					Stockpiles.totalsById(srv), "totals by id");
			h.assertValueEqual(4, Stockpiles.snapshot(srv).containers(), "containers");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-1b (roadmap: chests in a House are not counted) ------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_shared_wall", structure = Y, skyAccess = true, maxTicks = 500)
	public void sharedWallChestIsNotStockpile(GameTestHelper h) {
		BlockPos houseOrigin = new BlockPos(A.getX() + 6, 0, A.getZ());   // its west wall is the Storage's east wall
		BlockPos ms = markerPos(A, 5), mh = markerPos(houseOrigin, 5);
		BlockPos wallChest = new BlockPos(A.getX() + 6, 1, A.getZ() + 2);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			room(h, houseOrigin, 5, 4);
			bed(h, inside(houseOrigin, 1, 0, 0));
			h.setBlock(inside(houseOrigin, 3, 0, 0), Blocks.TORCH);
			h.setBlock(wallChest, Blocks.CHEST);
			fill(h, c1(A), stack(Items.OAK_LOG, 1));
			fill(h, c2(A), stack(Items.OAK_LOG, 1));
			fill(h, wallChest, stack(Items.APPLE, 7));
			placeMarker(h, BuildingType.STORAGE, ms, Direction.NORTH);
			placeMarker(h, BuildingType.HOUSE, mh, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "Storage counted", () -> {
			assertSites(h, 1);
			for (var v : Buildings.all(server(h))) h.assertTrue(v.latestScan().isPresent(), v.type() + " has a cached scan");
		});
		seq.thenExecute(() -> {
			MinecraftServer srv = server(h);
			Pos wall = pos(h, wallChest);
			for (var v : Buildings.all(srv)) {
				h.assertTrue(v.latestScan().get().positions("#chest").contains(wall),
						"guard: the " + v.type() + " scan lists the shared-wall chest (else this test proves nothing)");
			}
			h.assertValueEqual(0L, Stockpiles.count(srv, Items.APPLE), "wall chest apples");
			h.assertValueEqual(2, Stockpiles.snapshot(srv).containers(), "containers");
			h.assertValueEqual(2L, Stockpiles.count(srv, Items.OAK_LOG), "logs");
			ItemStack apples = stack(Items.APPLE, 5);
			h.assertValueEqual(5, Stockpiles.deposit(h.getLevel(), outsideDoor(h, A), apples), "stored");
			h.assertValueEqual(7, slots(h, wallChest).get(0).getCount(), "the wall chest still has exactly 7 apples");
			h.assertTrue(slots(h, wallChest).get(1).isEmpty(), "and nothing else");
			h.assertValueEqual(5L, Stockpiles.count(srv, Items.APPLE), "apples landed in a floor chest");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-2 (roadmap) ------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_spill", structure = Y, skyAccess = true, maxTicks = 500)
	public void depositSpillsIntoSecondStorage(GameTestHelper h) {
		BlockPos ma = markerPos(A, 5), mb = markerPos(B, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			storageRoom(h, B, Blocks.CHEST, Blocks.CHEST);
			fillAll(h, c1(A), stack(Items.DIRT, 64));
			ItemStack[] second = new ItemStack[27];
			for (int i = 0; i < 26; i++) second[i] = stack(Items.DIRT, 64);
			second[26] = stack(Items.COBBLESTONE, 30);
			fill(h, c2(A), second);
			placeMarker(h, BuildingType.STORAGE, ma, Direction.NORTH);
			placeMarker(h, BuildingType.STORAGE, mb, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "both Storages counted", () -> assertSites(h, 2));
		seq.thenExecute(() -> {
			ItemStack s = stack(Items.COBBLESTONE, 100);
			h.assertValueEqual(100, Stockpiles.deposit(h.getLevel(), outsideDoor(h, A), s), "stored");
			h.assertTrue(s.isEmpty(), "the caller's stack is used up");
			h.assertValueEqual(64, slots(h, c2(A)).get(26).getCount(), "A's cobblestone slot is topped up");
			h.assertValueEqual(64, slots(h, c1(B)).get(0).getCount(), "B first chest slot 0");
			h.assertValueEqual(2, slots(h, c1(B)).get(1).getCount(), "B first chest slot 1");
			h.assertValueEqual(130L, Stockpiles.count(server(h), Items.COBBLESTONE), "total cobblestone");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-3a ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_no_storage", structure = Y, skyAccess = true, maxTicks = 200)
	public void noStorageAlert(GameTestHelper h) {
		begin(h).thenIdle(Balance.STOCKPILE_REFRESH_TICKS + 2).thenExecute(() -> {
			h.assertValueEqual(StorageAlert.NO_STORAGE, Stockpiles.alert(server(h)), "alert");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-3b ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_full", structure = Y, skyAccess = true, maxTicks = 800)
	public void fullStorageAlertAndRemainder(GameTestHelper h) {
		BlockPos ma = markerPos(A, 5), mb = markerPos(B, 5);
		long[] shortAt = new long[1];
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			storageRoom(h, B, Blocks.CHEST, Blocks.CHEST);
			fillAll(h, c1(A), stack(Items.DIRT, 64));
			fillAll(h, c2(A), stack(Items.DIRT, 64));
			fillAll(h, c2(B), stack(Items.DIRT, 64));
			placeMarker(h, BuildingType.STORAGE, ma, Direction.NORTH);
			placeMarker(h, BuildingType.STORAGE, mb, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(2, 0), "both Storages counted", () -> assertSites(h, 2));
		seq = seq.thenExecute(() -> {
			ItemStack s = stack(Items.COBBLESTONE, 5);
			h.assertValueEqual(0, Stockpiles.depositHere(h.getLevel(), outsideDoor(h, A), s), "the full Storage takes nothing");
			h.assertValueEqual(5, s.getCount(), "the stack is intact");
		}).thenIdle(Balance.STOCKPILE_REFRESH_TICKS + 2).thenExecute(() ->
				h.assertValueEqual(StorageAlert.OK, Stockpiles.alert(server(h)), "one full Storage is not a full village"));
		seq = seq.thenExecute(() -> {
			fillAll(h, c1(B), stack(Items.DIRT, 64));
			ItemStack s = stack(Items.COBBLESTONE, 5);
			h.assertValueEqual(0, Stockpiles.deposit(h.getLevel(), outsideDoor(h, A), s), "nothing fits anywhere");
			h.assertValueEqual(5, s.getCount(), "the stack is intact");
			shortAt[0] = h.getLevel().getGameTime();
			h.assertFalse(Stockpiles.hasSpaceFor(h.getLevel(), stack(Items.COBBLESTONE, 1)), "no space for cobblestone");
		});
		seq = awaitWithin(h, seq, Balance.STOCKPILE_REFRESH_TICKS + 2, "alert rises", () ->
				h.assertValueEqual(StorageAlert.FULL, Stockpiles.alert(server(h)), "alert"));
		seq = seq.thenExecute(() -> {
			Container c = container(h, c1(B));
			c.setItem(0, ItemStack.EMPTY);
			c.setChanged();
			h.assertTrue(Stockpiles.hasSpaceFor(h.getLevel(), stack(Items.COBBLESTONE, 1)), "space at once");
		});
		awaitWithin(h, seq, Balance.STORAGE_FULL_HOLD_TICKS + 22, "alert clears", () -> {
			h.assertValueEqual(StorageAlert.OK, Stockpiles.alert(server(h)), "alert");
			h.assertTrue(h.getLevel().getGameTime() - shortAt[0] >= Balance.STORAGE_FULL_HOLD_TICKS, "not before the hold ran out");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	@GameTest(environment = GLUE_ENV, maxTicks = 100)
	public void offThreadRefused(GameTestHelper h) throws InterruptedException {
		MinecraftServer srv = server(h);
		Throwable[] seen = new Throwable[2];
		Thread t = new Thread(() -> {
			try { Stockpiles.snapshot(srv); } catch (Throwable e) { seen[0] = e; }
			try { Stockpiles.deposit(h.getLevel(), BlockPos.ZERO, stack(Items.DIRT, 1)); } catch (Throwable e) { seen[1] = e; }
		});
		t.start();
		t.join();
		h.assertTrue(seen[0] instanceof IllegalStateException, "snapshot refused off-thread: " + seen[0]);
		h.assertTrue(seen[1] instanceof IllegalStateException, "deposit refused off-thread: " + seen[1]);
		h.succeed();
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	private static final Item[] ITEMS = {Items.STONE, Items.DIRT, Items.COBBLESTONE, Items.OAK_LOG, Items.SPRUCE_LOG,
			Items.BIRCH_LOG, Items.BREAD, Items.APPLE, Items.CARROT, Items.POTATO, Items.WHEAT, Items.COAL, Items.IRON_INGOT, Items.GOLD_INGOT,
			Items.DIAMOND, Items.EMERALD, Items.STICK, Items.STRING, Items.PAPER, Items.BOOK, Items.SAND, Items.GRAVEL, Items.CLAY_BALL,
			Items.BRICK, Items.FLINT, Items.FEATHER, Items.BONE, Items.ARROW, Items.LEATHER, Items.EGG, Items.SUGAR, Items.REDSTONE};

	@GameTest(environment = "villageheart-gametest:stockpile_cost", structure = Y, skyAccess = true, maxTicks = 800)
	public void snapshotCostReal(GameTestHelper h) {
		BlockPos[] origins = {new BlockPos(2, 0, 4), new BlockPos(14, 0, 4), new BlockPos(2, 0, 16), new BlockPos(14, 0, 16)};
		BlockPos[] markers = new BlockPos[4];
		for (int i = 0; i < 4; i++) markers[i] = markerPos(origins[i], 8);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			int n = 0;
			for (int r = 0; r < 4; r++) {
				room(h, origins[r], 8, 4);
				for (int i = 0; i < 8; i++) {
					BlockPos chest = inside(origins[r], i, 0, 3);
					h.setBlock(chest, Blocks.CHEST);
					Container c = container(h, chest);
					for (int slot = 0; slot < 27; slot++) {
						int k = (n * 27 + slot) % 32;
						c.setItem(slot, slot % 5 == 0 ? named(ITEMS[k], 5, "n" + k) : stack(ITEMS[k], 5));
					}
					n++;
				}
				placeMarker(h, BuildingType.STORAGE, markers[r], Direction.NORTH);
			}
		});
		seq = awaitWithin(h, seq, bound(4, 0), "four Storages counted", () -> assertSites(h, 4));
		seq.thenExecute(() -> {
			MinecraftServer srv = server(h);
			var village = VillageClaims.current(srv).orElseThrow();
			StockSnapshot<StackKey> snap = Stockpiles.snapshot(srv);
			h.assertValueEqual(32, snap.containers(), "containers");
			h.assertValueEqual(32 * 27, snap.slots(), "slots");
			h.assertValueEqual(0, snap.freeSlots(), "every slot is filled");
			h.assertValueEqual(32L * 27 * 5, snap.totals().stream().mapToLong(StockSnapshot.Total::count).sum(), "total items");
			h.assertTrue(snap.totals().size() > 32, "named variants are separate totals: " + snap.totals().size());
			long[] snapT = new long[20], sysT = new long[20];
			for (int i = 0; i < 10; i++) {
				Stockpiles.snapshot(srv);
				Stockpiles.refreshAlert(srv);
			}
			for (int i = 0; i < 20; i++) {
				long a = System.nanoTime();
				Stockpiles.snapshot(srv);
				snapT[i] = System.nanoTime() - a;
				a = System.nanoTime();
				Stockpiles.refreshAlert(srv);
				sysT[i] = System.nanoTime() - a;
			}
			Arrays.sort(snapT);
			Arrays.sort(sysT);
			System.out.println("perf stockpile real snapshot (32 chests): median " + snapT[10] / 1000 + " us; refresh: median " + sysT[10] / 1000 + " us");
			h.assertTrue(snapT[10] < 3_000_000L, "snapshot median " + snapT[10] / 1000 + " us, budget 1000 us (asserted at 3x)");
			h.assertTrue(sysT[10] < 3_000_000L, "refresh median " + sysT[10] / 1000 + " us, budget 1000 us (asserted at 3x)");
			StockpileSystem.INSTANCE.tick(srv, village); // runs without throwing at any game time
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_new_chest", structure = Y, skyAccess = true, maxTicks = 500)
	public void newChestJoinsAfterRescan(GameTestHelper h) {
		BlockPos ma = markerPos(A, 5);
		BlockPos extra = inside(A, 2, 0, 3);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			placeMarker(h, BuildingType.STORAGE, ma, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(1, 0), "Storage counted", () -> assertSites(h, 1));
		seq = seq.thenExecute(() -> {
			h.setBlock(extra, Blocks.CHEST);
			fill(h, extra, stack(Items.OAK_LOG, 9));
		});
		seq = awaitWithin(h, seq, bound(1, 0), "third chest counted", () -> {
			h.assertValueEqual(9L, Stockpiles.count(server(h), Items.OAK_LOG), "logs");
			h.assertValueEqual(3, Stockpiles.snapshot(server(h)).containers(), "containers");
		});
		seq = seq.thenExecute(() -> h.setBlock(extra, Blocks.AIR));
		awaitWithin(h, seq, bound(1, 0), "third chest gone", () -> {
			h.assertValueEqual(0L, Stockpiles.count(server(h), Items.OAK_LOG), "logs");
			h.assertValueEqual(2, Stockpiles.snapshot(server(h)).containers(), "containers");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_invalid", structure = Y, skyAccess = true, maxTicks = 500)
	public void invalidStorageNotCounted(GameTestHelper h) {
		BlockPos ma = markerPos(A, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			fill(h, c1(A), stack(Items.OAK_LOG, 10));
			fill(h, c2(A), stack(Items.OAK_LOG, 10));
			placeMarker(h, BuildingType.STORAGE, ma, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(1, 0), "Storage counted", () -> {
			assertSites(h, 1);
			h.assertValueEqual(20L, Stockpiles.count(server(h), Items.OAK_LOG), "logs");
		});
		seq = seq.thenExecute(() -> {
			BlockPos door = new BlockPos(A.getX() + 3, 1, A.getZ());
			h.setBlock(door, Blocks.AIR);
			h.setBlock(door.above(), Blocks.AIR);
		});
		seq = awaitWithin(h, seq, bound(1, 0), "marker red", () -> assertStatus(h, ma, MarkerStatus.INVALID));
		seq.thenExecute(() -> {
			h.assertValueEqual(0L, Stockpiles.count(server(h), Items.OAK_LOG), "logs");
			h.assertValueEqual(0, Stockpiles.snapshot(server(h)).sites(), "sites");
		}).thenIdle(Balance.STOCKPILE_REFRESH_TICKS + 2).thenExecute(() -> {
			h.assertValueEqual(StorageAlert.NO_STORAGE, Stockpiles.alert(server(h)), "alert");
			VillageTestSupport.done(h);
		});
	}

	// ---- T-L2-12 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:stockpile_collector", structure = Y, skyAccess = true, maxTicks = 500)
	public void collectorReadsStockpile(GameTestHelper h) {
		BlockPos ma = markerPos(A, 5);
		GameTestSequence seq = begin(h).thenExecute(() -> {
			storageRoom(h, A, Blocks.CHEST, Blocks.CHEST);
			fill(h, c1(A), stack(Items.OAK_LOG, 20), stack(Items.BREAD, 10));
			fill(h, c2(A), stack(Items.COBBLESTONE, 64));
			placeMarker(h, BuildingType.STORAGE, ma, Direction.NORTH);
		});
		seq = awaitWithin(h, seq, bound(1, 0), "Storage counted", () -> assertSites(h, 1));
		seq.thenExecute(() -> {
			var collector = new dev.villageheart.sim.SimMetricsCollector(h.getLevel(), Vec3.atCenterOf(h.absolutePos(ma)), 40);
			var m = collector.collect(server(h), 1, h.getLevel().getGameTime(), TickStats.EMPTY, 1.0, 24000);
			h.assertValueEqual(Map.of("minecraft:bread", 10L, "minecraft:cobblestone", 64L, "minecraft:oak_log", 20L), m.stockpile(), "stockpile metric");
			VillageTestSupport.done(h);
		});
	}
}
