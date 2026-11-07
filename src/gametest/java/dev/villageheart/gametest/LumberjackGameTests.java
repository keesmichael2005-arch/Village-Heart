package dev.villageheart.gametest;

import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

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
				h.assertValueEqual(1, reloaded.treeTasks().size(), "the saved data holds the felling task");
				h.assertValueEqual(dev.villageheart.core.TreeTask.Stage.FELLING, reloaded.treeTasks().get(0).stage(), "as felling");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
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
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
