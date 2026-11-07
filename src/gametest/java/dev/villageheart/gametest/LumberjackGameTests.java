package dev.villageheart.gametest;

import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.tree.TreeCell;
import dev.villageheart.gametest.LumberTestSupport.Watcher;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.lumber.LevelTreeView;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

/**
 * Layer 2: the lumberjack (M9). Rules (docs/TESTING.md, "Lumberjack GameTests"): own environment per test, the {@code yard} structure with
 * {@code skyAccess}, {@code BuildingTestSupport.begin} first and {@code VillageTestSupport.done} last, deterministic trees (a configured
 * feature with a fixed seed), stray trees swept first, measured bounds. Every roadmap test logs its actual tick counts ({@code [m9] ...}).
 */
public class LumberjackGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:lumber_view", structure = YARD, skyAccess = true, maxTicks = 600)
	public void treeViewClassifies(GameTestHelper h) {
		begin(h);
		BlockPos[] base = new BlockPos[1];
		BlockPos logInRoom = BuildingTestSupport.inside(STORE, 2, 0, 0);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepStrayTrees(h, 48);
			grass(h, 12, 12, 26, 26);
			buildStorage(h);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, storeMarker(), net.minecraft.core.Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			base[0] = growOak(h, new BlockPos(20, 0, 20), 1L);
			h.setBlock(logInRoom, Blocks.OAK_LOG);
			h.setBlock(new BlockPos(24, 0, 24), Blocks.STRIPPED_OAK_LOG);
			h.setBlock(new BlockPos(25, 0, 24), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(BuildingTestSupport.verdict(h, storeMarker()).isPresent(), "a verdict"));
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			LevelTreeView view = new LevelTreeView(level);
			BlockPos b = base[0];
			h.assertValueEqual(TreeCell.Kind.LOG, view.cell(b.getX(), b.getY(), b.getZ()).kind(), "the trunk base is a log");
			h.assertValueEqual(TreeCell.Kind.SOIL, view.cell(b.getX(), b.getY() - 1, b.getZ()).kind(), "grass under the trunk is soil");
			Set<BlockPos> logs = logsOf(h, b);
			boolean sawNatural = false;
			for (int dx = -3; dx <= 3 && !sawNatural; dx++) for (int dy = 2; dy <= 8 && !sawNatural; dy++) for (int dz = -3; dz <= 3; dz++) {
				if (view.cell(b.getX() + dx, b.getY() + dy, b.getZ() + dz).kind() == TreeCell.Kind.NATURAL_LEAVES) {
					sawNatural = true;
					break;
				}
			}
			h.assertTrue(sawNatural, "the oak canopy reads as natural leaves (" + logs.size() + " logs)");
			BlockPos pl = h.absolutePos(new BlockPos(25, 0, 24));
			h.assertValueEqual(TreeCell.Kind.PLACED_LEAVES, view.cell(pl.getX(), pl.getY(), pl.getZ()).kind(), "persistent leaves");
			BlockPos st = h.absolutePos(new BlockPos(24, 0, 24));
			h.assertValueEqual(TreeCell.Kind.OTHER, view.cell(st.getX(), st.getY(), st.getZ()).kind(), "a stripped log is not a natural log");
			BlockPos rl = h.absolutePos(logInRoom);
			h.assertValueEqual(TreeCell.Kind.LOG, view.cell(rl.getX(), rl.getY(), rl.getZ()).kind(), "the log inside the room is a log");
			h.assertTrue(view.inRoom(new dev.villageheart.core.room.Pos(rl.getX(), rl.getY(), rl.getZ())), "and it is inside a valid room");
			h.assertFalse(view.inRoom(new dev.villageheart.core.room.Pos(b.getX(), b.getY(), b.getZ())), "the free oak is not");
			int far = b.getX() + 100_000;
			h.assertValueEqual(TreeCell.UNLOADED, view.cell(far, b.getY(), b.getZ()), "a far position is unloaded");
			h.assertValueEqual(Integer.MIN_VALUE, view.top(far, b.getZ()), "and has no height");
			h.assertFalse(level.hasChunk(far >> 4, b.getZ() >> 4), "reading it did not load the chunk");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:lumber_tools", structure = YARD, skyAccess = true, maxTicks = 900)
	public void campToolLease(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		BlockPos doubleLeft = BuildingTestSupport.inside(CAMP, 3, 0, 3);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepStrayTrees(h, 48);
			buildCamp(h);
			addSecondChop(h);
			placeMarkers(h, false);
		});
		seq = seq.thenWaitUntil(() -> assertValid(h, false));
		seq = seq.thenExecute(() -> {
			putInChest(h, chest(), 1, new ItemStack(Items.STICK));
			putInChest(h, chest(), 4, axe(ToolTier.STONE));
			putInChest(h, chest(), 9, axe(ToolTier.IRON));
			v[0] = hire(h, spawnSpot());
			v[1] = hire(h, spawnSpot());
		});
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			CampTools.Result a = CampTools.lease(server, v[0], ToolKind.AXE);
			CampTools.Result b = CampTools.lease(server, v[1], ToolKind.AXE);
			h.assertTrue(a instanceof CampTools.Held, "A holds a tool: " + a);
			h.assertTrue(b instanceof CampTools.Held, "B holds a tool: " + b);
			h.assertValueEqual(ToolTier.IRON, ((CampTools.Held) a).lease().tier(), "the first hired gets the iron axe");
			h.assertValueEqual(ToolTier.STONE, ((CampTools.Held) b).lease().tier(), "the second gets the stone axe");
			CampTools.Held ha = (CampTools.Held) a;
			h.assertTrue(CampTools.use(h.getLevel(), ha.lease(), 3), "the iron axe is used");
			h.assertValueEqual(3, StockpileTestSupport.container(h, chest()).getItem(9).getDamageValue(), "the iron axe lost 3 durability");
			// the player takes the iron axe out: A now gets the stone axe, B nothing
			StockpileTestSupport.container(h, chest()).setItem(9, ItemStack.EMPTY);
			CampTools.Result a2 = CampTools.lease(server, v[0], ToolKind.AXE);
			CampTools.Result b2 = CampTools.lease(server, v[1], ToolKind.AXE);
			h.assertTrue(a2 instanceof CampTools.Held && ((CampTools.Held) a2).lease().tier() == ToolTier.STONE, "A now holds the stone axe: " + a2);
			h.assertValueEqual(CampTools.Missing.NONE, b2, "B has no axe");
			// an axe at 1 durability left breaks to empty on use
			ItemStack frail = axe(ToolTier.STONE);
			frail.setDamageValue(frail.getMaxDamage() - 1);
			StockpileTestSupport.container(h, chest()).setItem(4, frail);
			CampTools.Held h2 = (CampTools.Held) CampTools.lease(server, v[0], ToolKind.AXE);
			h.assertTrue(CampTools.use(h.getLevel(), h2.lease(), 1), "used");
			h.assertTrue(StockpileTestSupport.container(h, chest()).getItem(4).isEmpty(), "the broken axe is gone from the slot");
			h.assertValueEqual(CampTools.Missing.NONE, CampTools.lease(server, v[0], ToolKind.AXE), "nothing left");
			// a double chest: one axe in the left half, two lumberjacks, exactly one gets it
			h.setBlock(doubleLeft, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
					net.minecraft.world.level.block.state.properties.ChestType.LEFT));
			h.setBlock(chest(), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
					net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
			putInChest(h, doubleLeft, 3, axe(ToolTier.IRON));
		});
		seq = seq.thenIdle(60);  // let the building be re-checked with both halves
		seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			CampTools.Result a = CampTools.lease(server, v[0], ToolKind.AXE);
			CampTools.Result b = CampTools.lease(server, v[1], ToolKind.AXE);
			int held = (a instanceof CampTools.Held ? 1 : 0) + (b instanceof CampTools.Held ? 1 : 0);
			h.assertValueEqual(1, held, "an axe in a double chest is given out exactly once: " + a + " / " + b);
			// a worker whose camp is not in the registry: the tool is unknown, not missing
			Villager stray = WorkerTestSupport.spawnVillager(h, spawnSpot());
			WorkerTestSupport.hireDirect(h, stray, dev.villageheart.core.worker.JobType.LUMBERJACK, chop());
			h.assertValueEqual(CampTools.Missing.UNKNOWN, CampTools.lease(server, stray, ToolKind.AXE), "unknown, so no 'Needs axe'");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	private static long stored(GameTestHelper h, String id) {
		return dev.villageheart.stock.Stockpiles.totalsById(h.getLevel().getServer()).getOrDefault(id, 0L);
	}

	private static String inventory(Villager v) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.getInventory().getContainerSize(); i++) if (!v.getInventory().getItem(i).isEmpty()) sb.append(v.getInventory().getItem(i)).append(' ');
		return sb.toString();
	}

	private static int carried(Villager v, net.minecraft.world.item.Item item) { return v.getInventory().countItem(item); }

	@GameTest(environment = "villageheart-gametest:lumber_fell", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void fellsOneOakWhole(GameTestHelper h) {
		begin(h);
		Watcher w = new Watcher();
		h.onEachTick(() -> w.sample(h));
		BlockPos treeRel = new BlockPos(2, 0, 8);
		Villager[] v = new Villager[1];
		BlockPos[] base = new BlockPos[1];
		Set<BlockPos>[] logs = new Set[1];
		long[] stat = new long[3];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			base[0] = growOak(h, treeRel, 1L);
			logs[0] = logsOf(h, base[0]);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			stat[0] = dev.villageheart.worker.job.WorkStats.get("lumberjack", "logs");
			stat[1] = dev.villageheart.worker.job.WorkStats.get("lumberjack", "trees");
			stat[2] = dev.villageheart.worker.job.WorkStats.get("lumberjack", "saplings_planted");
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[0].getInventory().addItem(new ItemStack(Items.OAK_SAPLING));
			w.start(h, logs[0]);
			t0[0] = h.getTick();
		});
		double dist = Math.sqrt(Math.pow(treeRel.getX() - chop().getX(), 2) + Math.pow(treeRel.getZ() - chop().getZ(), 2));
		int bound = fellBound(10, dist, ToolTier.STONE);
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[0]), "logs of the tree left; " + dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		seq = seq.thenExecute(() -> {
			int n = logs[0].size();
			long took = h.getTick() - t0[0];
			System.out.println("[m9] fellsOneOakWhole: " + n + " logs, last log after " + took + " ticks (bound " + fellBound(n, dist, ToolTier.STONE)
					+ "), gaps " + w.gaps());
			h.assertTrue(took <= fellBound(n, dist, ToolTier.STONE), "felling took " + took + " ticks, bound " + fellBound(n, dist, ToolTier.STONE));
			h.assertValueEqual(n, w.removals.size(), "every log was seen going");
			for (long gap : w.gaps()) h.assertTrue(Math.abs(gap - 40) <= 1, "gap between two logs is " + gap + ", expected 40");
			ItemStack axe = StockpileTestSupport.container(h, chest()).getItem(0);
			h.assertValueEqual(n, axe.getDamageValue(), "one durability per log");
			h.assertValueEqual(stat[0] + n, dev.villageheart.worker.job.WorkStats.get("lumberjack", "logs"), "work.lumberjack.logs");
			h.assertValueEqual(stat[1] + 1, dev.villageheart.worker.job.WorkStats.get("lumberjack", "trees"), "work.lumberjack.trees");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "a sapling stands on the stump", () ->
				h.assertTrue(h.getBlockState(treeRel).is(Blocks.OAK_SAPLING), "sapling at the base: " + h.getBlockState(treeRel)));
		seq.thenExecute(() -> {
			int n = logs[0].size();
			h.assertValueEqual((long) n, (long) carried(v[0], Items.OAK_LOG) + stored(h, "minecraft:oak_log"), "all the logs are carried or stored");
			h.assertValueEqual(stat[2] + 1, dev.villageheart.worker.job.WorkStats.get("lumberjack", "saplings_planted"), "work.lumberjack.saplings_planted");
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().treeTasks().isEmpty(),
					"the replant task is done and gone");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:lumber_pace", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void paceByTier(GameTestHelper h) {
		begin(h);
		Watcher w = new Watcher();
		h.onEachTick(() -> w.sample(h));
		ToolTier[] tiers = {ToolTier.WOOD, ToolTier.IRON, ToolTier.DIAMOND};
		double[] expected = {50.0, 100.0 / 3.0, 50.0 / 1.75};
		BlockPos[] spots = {new BlockPos(3, 0, 12), new BlockPos(3, 0, 20), new BlockPos(3, 0, 28)};
		Set<BlockPos>[] logs = new Set[3];
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, false, true);
		for (int i = 0; i < 3; i++) {
			final int k = i;
			seq = seq.thenExecute(() -> {
				BlockPos base = growOak(h, spots[k], 2L + k);
				logs[k] = logsOf(h, base);
				putInChest(h, chest(), 0, axe(tiers[k]));
				if (k == 0) {
					h.getLevel().setDayTime(1000);
					v[0] = hire(h, spawnSpot());
				}
				w.start(h, logs[k]);
			});
			seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[k]), "logs of tree " + k + " left; "
					+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
			seq = seq.thenExecute(() -> {
				var gaps = w.gaps();
				double mean = gaps.stream().mapToLong(Long::longValue).average().orElse(-1);
				System.out.println("[m9] paceByTier " + tiers[k] + ": " + logs[k].size() + " logs, gaps " + gaps + ", mean " + mean + ", expected " + expected[k]);
				h.assertTrue(gaps.size() >= 2, "enough logs to measure: " + gaps);
				h.assertTrue(Math.abs(mean - expected[k]) <= 1.0, tiers[k] + ": mean gap " + mean + ", expected " + expected[k]);
			});
		}
		seq.thenExecute(() -> {
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:lumber_drops", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void collectsDrops(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos spot = new BlockPos(22, 0, 22);
		BlockPos farStick = new BlockPos(31, 0, 31);
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[0].teleportTo(h.absolutePos(new BlockPos(25, 0, 27)).getX() + 0.5, h.absolutePos(new BlockPos(25, 0, 27)).getY(), h.absolutePos(new BlockPos(25, 0, 27)).getZ() + 0.5);
			h.spawnItem(Items.OAK_SAPLING, spot);
			h.spawnItem(Items.OAK_SAPLING, spot.east());
			for (int i = 0; i < 3; i++) h.spawnItem(Items.STICK, spot.south().east(i));
			h.spawnItem(Items.APPLE, spot.north());
			h.spawnItem(Items.DIRT, spot.north().east());
			h.spawnItem(Items.STICK, farStick);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the six wanted items are picked up", () -> {
			h.assertValueEqual(2, carried(v[0], Items.OAK_SAPLING), "saplings carried; " + inventory(v[0]) + " on the ground: "
					+ h.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream().map(e -> e.getItem() + "@" + e.blockPosition()).toList());
			h.assertValueEqual(3, carried(v[0], Items.STICK), "sticks carried");
			h.assertValueEqual(1, carried(v[0], Items.APPLE), "apples carried");
		});
		seq = seq.thenIdle(600);
		seq.thenExecute(() -> {
			int dirt = 0, sticks = 0, wanted = 0;
			for (var e : h.getEntities(net.minecraft.world.entity.EntityType.ITEM)) {
				if (e.getItem().is(Items.DIRT)) dirt++;
				else if (e.getItem().is(Items.STICK)) sticks++;
				else wanted++;
			}
			h.assertValueEqual(1, dirt, "the dirt lies where it was");
			h.assertValueEqual(1, sticks, "the stick outside the work area lies where it was");
			h.assertValueEqual(0, wanted, "nothing wanted is left on the ground");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:lumber_resume", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void fellResumesAfterInterrupt(GameTestHelper h) {
		begin(h);
		BlockPos spotA = new BlockPos(3, 0, 12), spotB = new BlockPos(3, 0, 24);
		Villager[] v = new Villager[2];
		Set<BlockPos>[] logs = new Set[2];
		BlockPos[] base = new BlockPos[2];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			base[0] = growOak(h, spotA, 11L);
			logs[0] = logsOf(h, base[0]);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
		});
		// (1) fired after the first log: the task stays and another lumberjack finishes the tree
		seq = seq.thenWaitUntil(() -> h.assertTrue(standing(h, logs[0]) < logs[0].size(), "the first log fell"));
		seq = seq.thenExecute(() -> {
			var village = dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow();
			h.assertValueEqual(1, village.treeTasks().size(), "one felling task is saved");
			h.assertValueEqual(dev.villageheart.core.TreeTask.Stage.FELLING, village.treeTasks().get(0).stage(), "and it is felling");
			h.assertTrue(Workers.fire(h.getLevel(), v[0]), "the first lumberjack is fired");
			h.assertTrue(standing(h, logs[0]) > 0, "the tree is not finished yet");
			v[1] = hire(h, spawnSpot());
			v[1].getInventory().addItem(new ItemStack(Items.OAK_SAPLING));
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[0]), "the second lumberjack finishes the tree; "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[1].getUUID(), v[1])));
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getBlockState(spotA).is(Blocks.OAK_SAPLING), "and replants: " + h.getBlockState(spotA)));
		seq = seq.thenWaitUntil(() -> h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().treeTasks().isEmpty(),
				"the task is gone"));
		// (2) a second oak: entity reload and a village-data reload after the first log
		seq = seq.thenExecute(() -> {
			base[1] = growOak(h, spotB, 12L);
			logs[1] = logsOf(h, base[1]);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(standing(h, logs[1]) < logs[1].size(), "a log of the second oak fell"));
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			try {
				var reloaded = VillageTestSupport.reload(server).village().orElseThrow();
				h.assertValueEqual(1, reloaded.treeTasks().size(), "the saved data holds the felling task: " + reloaded.treeTasks());
				h.assertValueEqual(dev.villageheart.core.TreeTask.Stage.FELLING, reloaded.treeTasks().get(0).stage(), "as felling");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			assertNoGiveUps(h);
			v[1] = reloadEntity(h.getLevel(), v[1]);
			dev.villageheart.worker.lumber.LumberRuntime.resetForTests();
			dev.villageheart.worker.job.WorkerStatuses.clearAll();
			h.assertTrue(standing(h, logs[1]) > 0, "the second oak is not finished yet");
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[1]), "the reloaded lumberjack finishes the second oak; "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[1].getUUID(), v[1])));
		seq = seq.thenWaitUntil(() -> h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().treeTasks().stream()
				.noneMatch(t -> t.stage() == dev.villageheart.core.TreeTask.Stage.FELLING), "no felling task is left"));
		seq.thenExecute(() -> {
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:lumber_keep", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void depositKeepsSaplings(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos cell = new BlockPos(3, 0, 29);
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			// one open replant spot of this camp, far from the Storage door
			var server = h.getLevel().getServer();
			var campLoc = dev.villageheart.world.VillageClaims.locate(h.getLevel(), h.absolutePos(campMarker()));
			var cellLoc = dev.villageheart.world.VillageClaims.locate(h.getLevel(), h.absolutePos(cell));
			var box = new dev.villageheart.core.room.Pos(cellLoc.x(), cellLoc.y(), cellLoc.z());
			dev.villageheart.world.VillageClaims.data(server).update(s -> s.withTreeTask(new dev.villageheart.core.TreeTask(campLoc,
					dev.villageheart.core.TreeTask.Stage.REPLANT, java.util.List.of(cellLoc), "minecraft:oak_sapling", h.getLevel().getGameTime(), box, box)));
			h.getLevel().setDayTime(3000);
			v[0] = hire(h, spawnSpot());
			v[0].getInventory().addItem(new ItemStack(Items.OAK_LOG, 64));
			v[0].getInventory().addItem(new ItemStack(Items.OAK_SAPLING, 6));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 700, "the 64 logs reach Storage", () ->
				h.assertValueEqual(64L, stored(h, "minecraft:oak_log"), "logs in Storage"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(5, carried(v[0], Items.OAK_SAPLING), "the worker keeps 1 + 4 saplings: " + inventory(v[0]));
			h.assertValueEqual(1L, stored(h, "minecraft:oak_sapling"), "the sixth sapling went to Storage");
			// the slot rule: seven different stacks leave one free slot, so the trip starts although the load is tiny
			var inv = v[0].getInventory();
			inv.clearContent();
			inv.addItem(new ItemStack(Items.OAK_SAPLING, 5));
			net.minecraft.world.item.Item[] junk = {Items.DIRT, Items.COBBLESTONE, Items.GRAVEL, Items.SAND, Items.CLAY_BALL, Items.FLINT};
			for (var item : junk) inv.addItem(new ItemStack(item, 1));
			int used = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) if (!inv.getItem(i).isEmpty()) used++;
			h.assertValueEqual(inv.getContainerSize() - 1, used, "one free slot: " + inventory(v[0]));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 700, "the junk reaches Storage although the load is light", () -> {
			h.assertValueEqual(1L, stored(h, "minecraft:flint"), "flint in Storage");
			h.assertValueEqual(1L, stored(h, "minecraft:dirt"), "dirt in Storage");
			h.assertTrue(carried(v[0], Items.OAK_SAPLING) >= 4, "the saplings stay with the worker: " + inventory(v[0]));
		});
		seq.thenExecute(() -> {
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-6 (roadmap) --------------------------------------------------------------------------

	/** Roadmap: 6 oaks + a stone axe give at least 16 logs in Storage within 2 days; felled spots are replanted within a day; a log wall is untouched. */
	@GameTest(environment = "villageheart-gametest:lumber_six_oaks", structure = YARD, skyAccess = true, maxTicks = 50000)
	public void sixOaksTwoDays(GameTestHelper h) {
		begin(h);
		BlockPos[] spots = {new BlockPos(3, 0, 21), new BlockPos(3, 0, 28), new BlockPos(12, 0, 17), new BlockPos(14, 0, 25),
				new BlockPos(27, 0, 12), new BlockPos(27, 0, 22)};
		BlockPos[] wall = new BlockPos[12];
		for (int i = 0; i < 12; i++) wall[i] = new BlockPos(i % 4, i / 4, 13);
		long[] goneAt = new long[6], plantedAt = new long[6];
		String[] failure = new String[1];
		BlockPos[] bases = new BlockPos[6];
		long[] t0 = new long[1], sixteenAt = new long[1];
		Villager[] v = new Villager[1];
		// every 100 ticks: replant bookkeeping and the wall (registered from the test body, active once the oaks exist)
		h.onEachTick(() -> {
			if (t0[0] == 0 || h.getTick() % 100 != 0) return;
			long now = h.getTick();
			for (int i = 0; i < 6; i++) {
				var s = h.getLevel().getBlockState(bases[i]);
				boolean log = s.is(net.minecraft.tags.BlockTags.OVERWORLD_NATURAL_LOGS);
				if (goneAt[i] == 0 && !log) goneAt[i] = now;
				if (goneAt[i] != 0 && plantedAt[i] == 0 && (s.is(net.minecraft.tags.BlockTags.SAPLINGS) || log)) plantedAt[i] = now;
				if (goneAt[i] != 0 && plantedAt[i] == 0 && now - goneAt[i] > 24000 && failure[0] == null) {
					failure[0] = "oak " + i + " went at tick " + goneAt[i] + " and was not replanted within a day";
				}
			}
			for (BlockPos w : wall) {
				if (!h.getBlockState(w).is(Blocks.OAK_LOG) && failure[0] == null) failure[0] = "a wall log at " + w + " is gone at tick " + now;
			}
		});
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			for (int i = 0; i < 6; i++) bases[i] = growOak(h, spots[i], 11L + i);
			for (BlockPos w : wall) h.setBlock(w, Blocks.OAK_LOG);
			lightYard(h);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			t0[0] = h.getTick() + 1;
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			long logs = dev.villageheart.stock.Stockpiles.count(h.getLevel().getServer(), net.minecraft.tags.ItemTags.LOGS);
			if (logs >= 16 && sixteenAt[0] == 0) sixteenAt[0] = h.getTick();
			h.assertTrue(sixteenAt[0] != 0, "16 logs in Storage (have " + logs + ")");
		});
		seq = seq.thenExecute(() -> {
			h.assertTrue(h.getTick() - t0[0] < 48000, "16 logs reached Storage within 48000 ticks");
			System.out.println("[m9] sixOaksTwoDays: 16 logs in Storage after " + (sixteenAt[0] - t0[0]) + " ticks");
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			for (int i = 0; i < 6; i++) h.assertTrue(plantedAt[i] != 0, "oak " + i + " is replanted (gone at " + goneAt[i] + ")");
		});
		// carry on through the night and into the second work day: the wall must still be whole (checked every 100 ticks)
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			h.assertTrue(h.getTick() - t0[0] >= 30000, "waiting for the second work day");
		});
		seq.thenExecute(() -> {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 6; i++) sb.append("oak ").append(i).append(": gone ").append(goneAt[i] - t0[0]).append(", replanted ").append(plantedAt[i] - t0[0]).append("; ");
			System.out.println("[m9] sixOaksTwoDays: " + sb + "tick now " + (h.getTick() - t0[0]));
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-7 (roadmap) --------------------------------------------------------------------------

	/** Roadmap: with no axe in the chest the lumberjack produces no logs in a day, and its reason is "Needs axe". */
	@GameTest(environment = "villageheart-gametest:lumber_no_axe", structure = YARD, skyAccess = true, maxTicks = 30000)
	public void noAxeNoLogs(GameTestHelper h) {
		begin(h);
		BlockPos treeRel = new BlockPos(2, 0, 8);
		Villager[] v = new Villager[1];
		BlockPos[] base = new BlockPos[1];
		Set<BlockPos>[] logs = new Set[1];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			base[0] = growOak(h, treeRel, 1L);
			logs[0] = logsOf(h, base[0]);
			lightYard(h);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the status becomes NEEDS_TOOL", () -> {
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, status(h, v[0]), "status");
		});
		seq = seq.thenExecute(() -> {
			System.out.println("[m9] noAxeNoLogs: NEEDS_TOOL after " + (h.getTick() - t0[0]) + " ticks");
			var text = dev.villageheart.worker.job.WorkerStatusText.of(dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, dev.villageheart.core.worker.JobType.LUMBERJACK);
			var contents = (net.minecraft.network.chat.contents.TranslatableContents) text.getContents();
			h.assertValueEqual("gui.villageheart.heart.activity.needs_tool.axe", contents.getKey(), "text key");
			h.assertValueEqual("Needs axe", contents.getFallback(), "text");
			// the Villagers tab row of the Heart screen says the same
			var row = HeartTestSupport.snapshot(h, dev.villageheart.core.Rank.OWNER).villagers().stream()
					.filter(r -> r.id().equals(v[0].getUUID())).findFirst().orElseThrow();
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.NEEDS_TOOL, row.activity(), "the row's activity");
			h.assertValueEqual("Needs axe", dev.villageheart.core.heart.HeartTexts.EN.get(
					dev.villageheart.core.heart.HeartTexts.activityKey(row.activity(), row.jobId())), "the row's text");
		});
		// a day of no axe: not a log falls, nothing reaches Storage
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(h.getTick() - t0[0] >= 24000, "waiting out a day");
		});
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(logs[0].size(), standing(h, logs[0]), "every log of the oak still stands");
			h.assertValueEqual(0L, dev.villageheart.stock.Stockpiles.count(h.getLevel().getServer(), net.minecraft.tags.ItemTags.LOGS), "no logs in Storage");
			h.assertValueEqual(0, carried(v[0], Items.OAK_LOG), "no logs carried");
			// control: the same village produces logs once there is an axe
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "NEEDS_TOOL goes away with an axe in the chest", () -> {
			h.assertTrue(status(h, v[0]) != dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, "still NEEDS_TOOL");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(standing(h, logs[0]) < logs[0].size(), "a log falls with the axe"));
		seq.thenExecute(() -> {
			System.out.println("[m9] noAxeNoLogs: first log with the axe after " + (h.getTick() - t0[0]) + " ticks");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	/** Odd tree shapes are felled whole: diagonal acacia trunk, cherry, a fancy oak with limbs, a 2x2 dark oak that needs four saplings. */
	@GameTest(environment = "villageheart-gametest:lumber_shapes", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void oddShapesFelledWhole(GameTestHelper h) {
		begin(h);
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>>[] keys = new net.minecraft.resources.ResourceKey[] {
				net.minecraft.data.worldgen.features.TreeFeatures.ACACIA, net.minecraft.data.worldgen.features.TreeFeatures.CHERRY,
				net.minecraft.data.worldgen.features.TreeFeatures.FANCY_OAK, net.minecraft.data.worldgen.features.TreeFeatures.DARK_OAK};
		// nearest first: the 40-log dark oak (and its replanting) comes last, so the day does not run out before the others are felled
		BlockPos[] spots = {new BlockPos(3, 0, 13), new BlockPos(17, 0, 13), new BlockPos(6, 0, 25), new BlockPos(22, 0, 24)};
		long[] seeds = {21, 22, 23, 24};
		Set<BlockPos>[] logs = new Set[4];
		java.util.List<BlockPos> darkBase = new java.util.ArrayList<>();
		Villager[] v = new Villager[1];
		long[] lastLogAt = new long[1], saplingsAt = new long[1];
		long[] goneAt = new long[4];
		boolean[] started = new boolean[1];
		// when each tree's initial logs were first all gone, and the moment the dark oak's four base cells hold saplings (a sampler: a
		// replanted sapling may grow into a new tree later, so "gone" is sticky)
		h.onEachTick(() -> {
			if (!started[0]) return;
			for (int i = 0; i < 4; i++) if (goneAt[i] == 0 && standing(h, logs[i]) == 0) goneAt[i] = h.getTick();
			lastLogAt[0] = goneAt[3];
			if (saplingsAt[0] == 0) {
				boolean all = true;
				for (BlockPos p : darkBase) all &= h.getLevel().getBlockState(p).is(Blocks.DARK_OAK_SAPLING);
				if (all) saplingsAt[0] = h.getTick();
			}
		});
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			for (int i = 0; i < 4; i++) {
				BlockPos base = grow(h, keys[i], spots[i], seeds[i]);
				logs[i] = logsOf(h, base);
				System.out.println("[m9] oddShapes tree " + i + " (" + keys[i].identifier().getPath() + "): " + logs[i].size() + " logs");
			}
			int minY = logs[3].stream().mapToInt(BlockPos::getY).min().orElseThrow();
			for (BlockPos p : logs[3]) if (p.getY() == minY) darkBase.add(p);
			h.assertValueEqual(4, darkBase.size(), "the dark oak has a 2x2 base");
			putInChest(h, chest(), 0, axe(ToolTier.IRON));
			putInChest(h, chest(), 1, new ItemStack(Items.DARK_OAK_SAPLING, 4));
			lightYard(h);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			started[0] = true;
		});
		seq = seq.thenWaitUntil(() -> {
			for (int i = 0; i < 4; i++) h.assertTrue(goneAt[i] != 0, "tree " + i + " (" + keys[i].identifier().getPath() + ") is felled whole, " + standing(h, logs[i])
					+ " logs stand; " + dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0]));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(saplingsAt[0] != 0, "the four dark oak saplings were planted; "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		seq.thenExecute(() -> {
			System.out.println("[m9] oddShapes: the four dark oak saplings stood " + (saplingsAt[0] - lastLogAt[0]) + " ticks after the last dark oak log");
			h.assertTrue(saplingsAt[0] - lastLogAt[0] <= 900, "four saplings within 900 ticks of the last dark oak log (measured 374 to 613): " + (saplingsAt[0] - lastLogAt[0]));
			assertValid(h, true);
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-13 ---------------------------------------------------------------------------------

	/** Two lumberjacks in one camp never share a tree and an axe; with one axe removed the later hired one has no tool. */
	@GameTest(environment = "villageheart-gametest:lumber_two", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void twoLumberjacksOneCamp(GameTestHelper h) {
		begin(h);
		BlockPos[] spots = {new BlockPos(3, 0, 14), new BlockPos(3, 0, 21), new BlockPos(3, 0, 28)};
		Set<BlockPos>[] logs = new Set[3];
		BlockPos[] bases = new BlockPos[3];
		Villager[] v = new Villager[2];
		java.util.List<java.util.Set<java.util.UUID>> breakers = new java.util.ArrayList<>();
		for (int i = 0; i < 3; i++) breakers.add(new java.util.HashSet<>());
		int[] last = new int[3];
		boolean[] started = new boolean[1];
		String[] failure = new String[1];
		h.onEachTick(() -> {
			if (!started[0]) return;
			var rt = dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer());
			var claims = rt.claims();
			for (int i = 0; i < 3; i++) {
				int now = standing(h, logs[i]);
				if (now < last[i]) {
					var key = dev.villageheart.world.VillageClaims.locate(h.getLevel(), bases[i]);
					java.util.UUID owner = claims.get(key);
					if (owner == null && failure[0] == null && now > 0) failure[0] = "a log of tree " + i + " fell with no claim";
					if (owner != null) breakers.get(i).add(owner);
				}
				last[i] = now;
			}
			java.util.Set<java.util.UUID> distinct = new java.util.HashSet<>(claims.values());
			// one claim per worker
			if (claims.size() > 2 && failure[0] == null) failure[0] = "more claims than workers: " + claims;
			if (distinct.size() < claims.size() && failure[0] == null) failure[0] = "a worker holds two claims: " + claims;
		});
		GameTestSequence seq = scene(h, true, true).thenExecute(() -> {
			for (int i = 0; i < 3; i++) {
				bases[i] = growOak(h, spots[i], 31L + i);
				logs[i] = logsOf(h, bases[i]);
				last[i] = logs[i].size();
			}
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			putInChest(h, chest(), 1, axe(ToolTier.STONE));
			lightYard(h);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[1] = hire(h, BuildingTestSupport.inside(CAMP, 3, 0, 2));
			started[0] = true;
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			for (int i = 0; i < 3; i++) h.assertValueEqual(0, standing(h, logs[i]), "logs left of tree " + i);
		});
		seq = seq.thenExecute(() -> {
			for (int i = 0; i < 3; i++) h.assertValueEqual(1, breakers.get(i).size(), "tree " + i + " was felled by exactly one worker: " + breakers.get(i));
			java.util.Set<java.util.UUID> all = new java.util.HashSet<>();
			for (var b : breakers) all.addAll(b);
			System.out.println("[m9] twoLumberjacks: " + all.size() + " different workers felled the 3 trees");
			// one axe removed: only the first hired keeps one
			StockpileTestSupport.container(h, chest()).setItem(1, ItemStack.EMPTY);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the later hired lumberjack needs an axe", () -> {
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, status(h, v[1]), "second lumberjack");
			h.assertTrue(status(h, v[0]) != dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, "first lumberjack keeps its axe");
		});
		seq.thenExecute(() -> {
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-14 ---------------------------------------------------------------------------------

	/** A lumberjack that cannot hand in its load stops cutting; once there is room it delivers and goes on. */
	@GameTest(environment = "villageheart-gametest:lumber_full", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void storageFullStopsGathering(GameTestHelper h) {
		begin(h);
		BlockPos treeRel = new BlockPos(3, 0, 14);
		Villager[] v = new Villager[1];
		Set<BlockPos>[] logs = new Set[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			BlockPos base = growOak(h, treeRel, 41L);
			logs[0] = logsOf(h, base);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			StockpileTestSupport.fillAll(h, storeChest1(), new ItemStack(Items.COBBLESTONE, 64));
			StockpileTestSupport.fillAll(h, storeChest2(), new ItemStack(Items.COBBLESTONE, 64));
			lightYard(h);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[0].getInventory().addItem(new ItemStack(Items.OAK_LOG, 64));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "STORAGE_FULL is reported", () ->
				h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.STORAGE_FULL, status(h, v[0]), "status"));
		seq = seq.thenIdle(1200);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(logs[0].size(), standing(h, logs[0]), "not a log fell while the load could not be handed in");
			h.assertValueEqual(64, carried(v[0], Items.OAK_LOG), "the worker keeps what it carries");
			// room in one chest: the load goes in, then the tree falls
			var c = StockpileTestSupport.container(h, storeChest1());
			c.clearContent();
			c.setChanged();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 700, "the 64 logs reach Storage", () ->
				h.assertTrue(stored(h, "minecraft:oak_log") >= 64, "logs in Storage: " + stored(h, "minecraft:oak_log")));
		seq = seq.thenWaitUntil(() -> h.assertTrue(standing(h, logs[0]) < logs[0].size(), "felling resumes; "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		seq.thenExecute(() -> {
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	private static void bigRoom(GameTestHelper h, BlockPos o) {
		// shell x o..o+10, y 0..10, z o..o+10 (interior 9 x 9 x 9), door in the north wall at x = o + 5
		for (int x = 0; x <= 10; x++) for (int y = 0; y <= 10; y++) for (int z = 0; z <= 10; z++) {
			boolean edge = x == 0 || x == 10 || y == 0 || y == 10 || z == 0 || z == 10;
			h.setBlock(o.offset(x, y, z), edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
		}
		WorkerTestSupport.door(h, new BlockPos(o.getX() + 5, o.getY() + 1, o.getZ()), net.minecraft.core.Direction.NORTH);
		h.setBlock(o.offset(1, 1, 9), JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
		h.setBlock(o.offset(9, 1, 9), Blocks.CHEST);
		h.setBlock(o.offset(8, 1, 9), Blocks.TORCH);
	}

	/**
	 * Player-made logs are left alone: a log wall under natural leaves, a "tree" with persistent leaves, a natural oak inside a valid room, and
	 * a log cabin with an oak against its wall. The one free oak by the wall is felled. Afterwards the worker is out of work ("no trees"), and a
	 * free oak grown later is felled, which proves it was searching all along.
	 */
	@GameTest(environment = "villageheart-gametest:lumber_placed", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void placedLogsUntouched(GameTestHelper h) {
		begin(h);
		BlockPos bigOrigin = new BlockPos(17, 0, 13);
		BlockPos bigMarker = new BlockPos(bigOrigin.getX() + 5, bigOrigin.getY() + 3, bigOrigin.getZ() - 1);
		BlockPos wallOak = new BlockPos(3, 0, 18);
		java.util.List<BlockPos> wall = new java.util.ArrayList<>();
		for (int x = 2; x <= 5; x++) for (int y = 0; y <= 2; y++) wall.add(new BlockPos(x, y, 16));
		BlockPos fakeTree = new BlockPos(3, 0, 26);
		java.util.List<BlockPos> protectedLogs = new java.util.ArrayList<>(wall);
		for (int y = 0; y <= 3; y++) protectedLogs.add(fakeTree.above(y));
		java.util.List<BlockPos> cabin = new java.util.ArrayList<>();
		for (int y = 0; y <= 2; y++) for (int x = 24; x <= 28; x++) for (int z = 25; z <= 29; z++) {
			if (x == 24 || x == 28 || z == 25 || z == 29) cabin.add(new BlockPos(x, y, z));
		}
		protectedLogs.addAll(cabin);
		Villager[] v = new Villager[1];
		Set<BlockPos>[] oakLogs = new Set[3]; // wall oak, room oak, cabin oak
		Set<BlockPos>[] lateOak = new Set[1];
		BlockPos[] bases = new BlockPos[3];
		boolean[] started = new boolean[1];
		String[] failure = new String[1];
		long[] noWorkAt = new long[1];
		h.onEachTick(() -> {
			if (!started[0] || h.getTick() % 100 != 0) return;
			for (BlockPos p : protectedLogs) {
				if (!h.getBlockState(p).is(net.minecraft.tags.BlockTags.OVERWORLD_NATURAL_LOGS) && failure[0] == null) failure[0] = "a player log at " + p + " is gone at tick " + h.getTick();
			}
			for (int i = 1; i < 3; i++) {
				if (standing(h, oakLogs[i]) < oakLogs[i].size() && failure[0] == null) failure[0] = "a log of the protected oak " + i + " was felled at tick " + h.getTick();
			}
		});
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			sweepStrayTrees(h, 48);
			grass(h, 0, 0, 31, 31);
			lightYard(h);
			buildCamp(h);
			buildStorage(h);
			bigRoom(h, bigOrigin);
			placeMarkers(h, true);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.LUMBER_CAMP, bigMarker, net.minecraft.core.Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			assertValid(h, true);
			BuildingTestSupport.assertStatus(h, bigMarker, dev.villageheart.block.MarkerStatus.VALID);
		});
		seq = seq.thenExecute(() -> {
			// the room first: its oak grows inside it
			bases[1] = growOak(h, new BlockPos(bigOrigin.getX() + 5, 1, bigOrigin.getZ() + 5), 51L);
			oakLogs[1] = logsOf(h, bases[1]);
			// a wall of logs with natural leaves on its top, and a free oak a block away
			for (BlockPos p : wall) h.setBlock(p, Blocks.OAK_LOG);
			for (int x = 2; x <= 5; x++) h.setBlock(new BlockPos(x, 3, 16), Blocks.OAK_LEAVES);
			bases[0] = growOak(h, wallOak, 52L);
			oakLogs[0] = logsOf(h, bases[0]);
			// a trunk of logs with persistent leaves
			for (int y = 0; y <= 3; y++) h.setBlock(fakeTree.above(y), Blocks.OAK_LOG);
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 2; dy <= 4; dy++) {
				if (dx == 0 && dz == 0 && dy < 4) continue;
				h.setBlock(fakeTree.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			}
			// a cabin of logs with an oak against its wall
			for (BlockPos p : cabin) h.setBlock(p, Blocks.OAK_LOG);
			bases[2] = growOak(h, new BlockPos(23, 0, 27), 53L);
			oakLogs[2] = logsOf(h, bases[2]);
			oakLogs[2].removeAll(cabin.stream().map(h::absolutePos).toList());
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
		});
		seq = seq.thenWaitUntil(() -> {
			BlockPos any = oakLogs[1].iterator().next();
			h.assertTrue(new LevelTreeView(h.getLevel()).inRoom(new dev.villageheart.core.room.Pos(any.getX(), any.getY(), any.getZ())),
					"the room's scan holds the oak");
		});
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			h.assertTrue(dev.villageheart.building.Buildings.valid(server, dev.villageheart.core.building.BuildingType.LUMBER_CAMP).size() == 2,
					"both Lumber Camps are valid, the second holds the oak");
			// premise: the wall touches at least three natural leaf blocks
			var view = new LevelTreeView(h.getLevel());
			java.util.Set<BlockPos> leaves = new java.util.HashSet<>();
			for (BlockPos p : wall) {
				BlockPos a = h.absolutePos(p);
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
					BlockPos n = a.relative(d);
					if (view.cell(n.getX(), n.getY(), n.getZ()).kind() == TreeCell.Kind.NATURAL_LEAVES) leaves.add(n);
				}
			}
			h.assertTrue(leaves.size() >= 3, "the wall touches natural leaves: " + leaves.size());
			h.assertTrue(oakLogs[1].size() >= 3, "the room holds an oak");
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			started[0] = true;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, fellBound(8, 13, ToolTier.STONE) + 200, "the free oak by the wall is felled", () ->
				h.assertValueEqual(0, standing(h, oakLogs[0]), "wall oak logs left; " + dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		// from now on the worker must find nothing to do: NO_WORK, every protected log in place
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.NO_WORK, status(h, v[0]), "no trees to fell; "
					+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0]));
		});
		seq = seq.thenExecute(() -> noWorkAt[0] = h.getTick());
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			h.assertTrue(h.getTick() - noWorkAt[0] >= 1500, "sampling for 1500 ticks");
			h.assertTrue(status(h, v[0]) == dev.villageheart.core.worker.WorkerStatus.NO_WORK, "still NO_WORK: " + status(h, v[0]));
		});
		seq = seq.thenExecute(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			System.out.println("[m9] placedLogsUntouched: NO_WORK from " + noWorkAt[0] + ", protected logs whole 1500 ticks later at " + h.getTick());
			// the worker was searching all along: a free oak 12 blocks from the Chopping Block is found and felled
			BlockPos b = growOak(h, new BlockPos(9, 0, 17), 54L);
			lateOak[0] = logsOf(h, b);
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, lateOak[0]), "the late oak is felled; "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		seq.thenExecute(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-12 ---------------------------------------------------------------------------------

	/** The nearest tree is out of reach (inside a wide glass box): it is tried, given up and left alone; the next one is felled; one beyond 32 blocks never. */
	@GameTest(environment = "villageheart-gametest:lumber_radius", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void radiusAndUnreachable(GameTestHelper h) {
		begin(h);
		BlockPos aSpot = new BlockPos(9, 0, 20), bSpot = new BlockPos(29, 0, 17), cSpot = new BlockPos(30, 0, 30);
		BlockPos[] base = new BlockPos[3];
		Set<BlockPos>[] logs = new Set[3];
		Villager[] v = new Villager[1];
		long[] claimedAt = new long[1], givenUpAt = new long[1];
		boolean[] started = new boolean[1];
		h.onEachTick(() -> {
			if (!started[0]) return;
			var rt = dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer());
			var keyA = dev.villageheart.world.VillageClaims.locate(h.getLevel(), base[0]);
			if (claimedAt[0] == 0 && rt.claims().containsKey(keyA)) claimedAt[0] = h.getTick();
			if (claimedAt[0] != 0 && givenUpAt[0] == 0 && !rt.blacklist(dev.villageheart.world.VillageClaims.locate(h.getLevel(), h.absolutePos(campMarker()))).isEmpty()) {
				givenUpAt[0] = h.getTick();
			}
		});
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			// a glass box around A, walls 9 blocks from its trunk (out of reach, 3 high: villagers cannot climb it)
			for (int x = aSpot.getX() - 9; x <= aSpot.getX() + 9; x++) for (int z = aSpot.getZ() - 9; z <= aSpot.getZ() + 9; z++) {
				boolean edge = Math.abs(x - aSpot.getX()) == 9 || Math.abs(z - aSpot.getZ()) == 9;
				if (!edge) continue;
				if (z < 11 && x >= 7 && x <= 15) continue; // never overlaps the camp
				for (int y = 0; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
			}
			base[0] = growOak(h, aSpot, 61L);
			base[1] = growOak(h, bSpot, 62L);
			base[2] = growOak(h, cSpot, 63L);
			for (int i = 0; i < 3; i++) logs[i] = logsOf(h, base[i]);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			lightYard(h);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			started[0] = true;
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(claimedAt[0] != 0, "tree A (the nearest, in the box) is tried first"));
		seq = seq.thenWaitUntil(() -> h.assertTrue(givenUpAt[0] != 0 || standing(h, logs[1]) < logs[1].size(), "tree A is given up (or B already falls); "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		seq = seq.thenExecute(() -> System.out.println("[m9] radiusAndUnreachable: A claimed at " + claimedAt[0] + ", given up at " + givenUpAt[0]));
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[1]), "tree B is felled; "
				+ dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).describe(v[0].getUUID(), v[0])));
		seq = seq.thenIdle(1500);
		seq.thenExecute(() -> {
			h.assertValueEqual(logs[0].size(), standing(h, logs[0]), "tree A (unreachable) is untouched");
			h.assertValueEqual(logs[2].size(), standing(h, logs[2]), "tree C (beyond 32 blocks) is untouched");
			h.assertTrue(givenUpAt[0] - claimedAt[0] <= 830, "A was given up within 800 ticks: " + (givenUpAt[0] - claimedAt[0]));
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
