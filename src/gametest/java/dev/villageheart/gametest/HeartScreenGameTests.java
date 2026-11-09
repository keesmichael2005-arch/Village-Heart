package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.*;
import static dev.villageheart.gametest.HeartTestSupport.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.BuildingRow;
import dev.villageheart.core.heart.DetailKind;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.heart.RowStatus;
import dev.villageheart.core.heart.StockLine;
import dev.villageheart.core.heart.VillagerRow;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.Balance;
import dev.villageheart.heart.HeartFacts;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.heart.net.HeartSnapshotPayload;
import dev.villageheart.world.VillageClaims;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Layer 2: the Heart screen (M8). Rules as for the building tests: own environment per test, the yard structure, skyAccess,
 * {@link HeartTestSupport#seed} first and {@link HeartTestSupport#done} last, measured waits.
 */
public class HeartScreenGameTests {
	private static MinecraftServer server(GameTestHelper h) { return h.getLevel().getServer(); }

	// ---- T-L2-3 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_visitor", structure = YARD, skyAccess = true, maxTicks = 600)
	public void visitorOnlyGetsOverview(GameTestHelper h) {
		Villager[] v = new Villager[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			recorder();
			v[0] = villager(h);
			h.assertTrue(Workers.hire(h.getLevel(), v[0], JobType.FARMER, Optional.empty()) instanceof HireResult.Hired, "hired");
		});
		seq.thenIdle(3).thenExecute(() -> {
			HeartSnapshot vis = open(h, player(h, "vis", Rank.VISITOR)).snapshot();
			h.assertValueEqual(Rank.VISITOR, vis.viewerRank(), "visitor rank");
			h.assertTrue(vis.villagers().isEmpty() && vis.buildings().isEmpty() && vis.stock().items().isEmpty()
					&& vis.jobChoices().isEmpty(), "no details for a Visitor");
			h.assertTrue(vis.overview().population() >= 1, "overview population");
			h.assertValueEqual(1, vis.overview().workers(), "overview workers");

			HeartSnapshot res = open(h, player(h, "res", Rank.RESIDENT)).snapshot();
			h.assertValueEqual(Rank.RESIDENT, res.viewerRank(), "resident rank");
			h.assertValueEqual(1, res.villagers().size(), "resident sees the villager");
			h.assertValueEqual(2, res.buildings().size(), "resident sees both buildings");
			h.assertTrue(res.stock().items().stream().anyMatch(i -> i.itemId().equals("minecraft:oak_log") && i.count() == 64), "64 logs");
			h.assertTrue(res.jobChoices().isEmpty(), "a Resident cannot hire");

			HeartSnapshot st = open(h, player(h, "stew", Rank.STEWARD)).snapshot();
			h.assertValueEqual(Rank.STEWARD, st.viewerRank(), "steward rank");
			h.assertValueEqual(0, st.jobChoices().stream().filter(c -> c.jobId().equals("farmer")).findFirst().orElseThrow().freeSlots(),
					"farmer has no free slot");
			done(h);
		});
	}

	// ---- T-L2-4 (rank part) ------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_rank", structure = YARD, skyAccess = true, maxTicks = 600)
	public void rankChangeWhileOpen(GameTestHelper h) {
		ServerPlayer[] s = new ServerPlayer[1];
		List<Sent>[] sent = new List[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			sent[0] = recorder();
			villager(h);
			s[0] = player(h, "stew", Rank.STEWARD);
			open(h, s[0]);
			VillageClaims.setRank(server(h), s[0].getUUID(), Rank.VISITOR);
		});
		seq = awaitWithin(h, seq, Balance.HEART_REFRESH_TICKS + 1, "the demotion reaches the open screen", () -> {
			List<HeartSnapshotPayload> got = snapshotsTo(sent[0], s[0]);
			h.assertFalse(got.isEmpty(), "a snapshot was sent");
			HeartSnapshot last = got.get(got.size() - 1).snapshot();
			h.assertValueEqual(Rank.VISITOR, last.viewerRank(), "viewer rank");
			h.assertTrue(last.villagers().isEmpty() && last.buildings().isEmpty() && last.jobChoices().isEmpty(), "no rows");
		});
		seq = seq.thenExecute(() -> VillageClaims.setRank(server(h), s[0].getUUID(), Rank.STEWARD));
		awaitWithin(h, seq, Balance.HEART_REFRESH_TICKS + 1, "the promotion reaches the open screen", () -> {
			List<HeartSnapshotPayload> got = snapshotsTo(sent[0], s[0]);
			HeartSnapshot last = got.get(got.size() - 1).snapshot();
			h.assertValueEqual(Rank.STEWARD, last.viewerRank(), "viewer rank");
			h.assertFalse(last.villagers().isEmpty() || last.buildings().isEmpty() || last.jobChoices().isEmpty(), "rows and choices back");
		}).thenExecute(() -> done(h));
	}

	// ---- T-L2-6 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_close", structure = YARD, skyAccess = true, maxTicks = 600)
	public void menuClosesWhenFarOrHeartGone(GameTestHelper h) {
		ServerPlayer[] s = new ServerPlayer[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			recorder();
			s[0] = player(h, "stew", Rank.STEWARD);
			open(h, s[0]);
			BlockPos hp = heartPos(h);
			s[0].setPos(hp.getX() + 0.5, hp.getY(), hp.getZ() + 12.5);
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			h.assertTrue(s[0].containerMenu == s[0].inventoryMenu, "walking away closed the screen");
			open(h, s[0]);
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			h.assertTrue(s[0].containerMenu instanceof HeartMenu, "still open next to the Heart");
			h.getLevel().setBlock(heartPos(h), Blocks.AIR.defaultBlockState(), 3);
		});
		seq.thenIdle(2).thenExecute(() -> {
			h.assertTrue(s[0].containerMenu == s[0].inventoryMenu, "breaking the Heart closed the screen");
			done(h);
		});
	}

	// ---- T-L2-8 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_refresh", structure = YARD, skyAccess = true, maxTicks = 700)
	public void refreshOnlyWhenChanged(GameTestHelper h) {
		ServerPlayer[] p = new ServerPlayer[2];
		List<Sent>[] sent = new List[1];
		int[] countBefore = new int[1];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			sent[0] = recorder();
			p[0] = player(h, "stew", Rank.STEWARD);
			open(h, p[0]);
		});
		seq = seq.thenExecuteFor(40, () -> h.assertTrue(snapshotsTo(sent[0], p[0]).isEmpty(), "nothing changed, nothing sent"));
		seq = seq.thenExecute(() -> StockpileTestSupport.fill(h, chest2(), new ItemStack(Items.BREAD, 21)));
		seq = awaitWithin(h, seq, Balance.HEART_REFRESH_TICKS + 1, "the bread change reaches the screen", () -> {
			List<HeartSnapshotPayload> got = snapshotsTo(sent[0], p[0]);
			h.assertValueEqual(1, got.size(), "exactly one snapshot");
			h.assertTrue(got.get(0).snapshot().stock().items().stream()
					.anyMatch(i -> i.itemId().equals("minecraft:bread") && i.count() == 21), "bread 21");
		});
		seq = seq.thenExecute(() -> {
			p[1] = player(h, "owner2", Rank.STEWARD);
			open(h, p[1]);
			HeartMenu.markAllDirty(server(h));
			HeartFacts.invalidate();
			countBefore[0] = HeartFacts.collectCount;
		});
		seq.thenIdle(3).thenExecute(() -> {
			h.assertValueEqual(countBefore[0] + 1, HeartFacts.collectCount, "two open screens refreshed in one tick, one collect");
			done(h);
		});
	}

	// ---- T-L2-11 --------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_unclaimed", structure = YARD, skyAccess = true, maxTicks = 300)
	public void unclaimedHeartSaysSo(GameTestHelper h) {
		BlockPos other = new BlockPos(20, 1, 20);
		GameTestSequence seq = seed(h).thenExecute(() -> {
			recorder();
			h.setBlock(other, ModBlocks.VILLAGE_HEART);
			OverlayPlayer p = (OverlayPlayer) VillageTestSupport.player(h, "visitor", GameType.SURVIVAL, OverlayPlayer::new);
			BlockPos abs = h.absolutePos(other);
			p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
			rightClickAbs(h.getLevel(), p, abs, Direction.UP, ItemStack.EMPTY);
			h.assertTrue(p.containerMenu == p.inventoryMenu, "no menu opened");
			h.assertTrue(p.overlays.size() == 1 && translatable(p.overlays.get(0)).getKey().equals("message.villageheart.heart.not_this_village"),
					"action bar says so: " + p.overlays);
			h.setBlock(other, Blocks.AIR);
			done(h);
		});
	}

	// ---- T-L2-7 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:heart_facts", structure = YARD, skyAccess = true, maxTicks = 600)
	public void snapshotOfSeededVillage(GameTestHelper h) {
		Villager[] v = new Villager[3];
		GameTestSequence seq = seed(h).thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			for (int i = 0; i < 3; i++) v[i] = villager(h);
			h.assertTrue(Workers.hire(h.getLevel(), v[0], JobType.FARMER, Optional.empty()) instanceof HireResult.Hired, "hired");
		});
		seq.thenIdle(5).thenExecute(() -> {
			HeartSnapshot s = snapshot(h, Rank.OWNER);
			h.assertValueEqual(3, s.overview().population(), "population");
			h.assertValueEqual(3, s.overview().adults(), "adults");
			h.assertValueEqual(0, s.overview().babies(), "babies");
			h.assertValueEqual(0, s.overview().beds(), "beds");
			h.assertValueEqual(1, s.overview().workers(), "workers");
			h.assertValueEqual(1, s.overview().workerSlots(), "worker slots");
			h.assertValueEqual(33, s.overview().foodDaysTenths(), "food days (20 bread x 5 = 100 / 30)");
			h.assertValueEqual(0, s.overview().treasury(), "treasury");
			h.assertValueEqual(1, s.overview().wageBill(), "wage bill: one level 1 worker (M12)");
			// M12: the one worker costs 1 emerald a day and the treasury is empty, so the only alert is the short-treasury warning
			h.assertValueEqual(java.util.List.of(dev.villageheart.core.heart.AlertCode.TREASURY_SHORT),
					s.overview().alerts().stream().map(dev.villageheart.core.heart.AlertLine::code).toList(), "alerts: " + s.overview().alerts());

			h.assertValueEqual(3, s.villagers().size(), "villager rows");
			VillagerRow w = s.villagers().get(0);
			h.assertValueEqual(v[0].getUUID(), w.id(), "the worker is first");
			h.assertValueEqual("farmer", w.jobId(), "job");
			h.assertValueEqual(1, w.level(), "level");
			h.assertValueEqual(0, w.xp(), "xp");
			h.assertValueEqual(100, w.xpForNext(), "xp for next");
			h.assertTrue(w.needs().known() && w.needs().fed() && w.needs().housed() && w.needs().paid(), "needs known and met");
			h.assertTrue(w.loaded(), "loaded");
			h.assertTrue(w.activity() != ActivityKind.AWAY, "a known activity, got " + w.activity());
			System.out.println("heart facts: worker activity " + w.activity());
			h.assertTrue(!s.villagers().get(1).isWorker() && !s.villagers().get(2).isWorker(), "the others are unemployed");

			h.assertValueEqual(2, s.buildings().size(), "building rows");
			BuildingRow farm = s.buildings().stream().filter(b -> b.typeId().equals(BuildingType.FARMHOUSE.id())).findFirst().orElseThrow();
			h.assertValueEqual(RowStatus.VALID, farm.status(), "farmhouse status");
			h.assertValueEqual(DetailKind.WORKER_SLOTS, farm.detail(), "farmhouse detail");
			h.assertValueEqual(1, farm.detailCount(), "farmhouse slots");
			h.assertValueEqual(1, farm.workersHere(), "workers at the farmhouse");
			BuildingRow store = s.buildings().stream().filter(b -> b.typeId().equals(BuildingType.STORAGE.id())).findFirst().orElseThrow();
			h.assertValueEqual(RowStatus.VALID, store.status(), "storage status");
			h.assertValueEqual(DetailKind.CONTAINERS, store.detail(), "storage detail");
			h.assertValueEqual(2, store.detailCount(), "containers");

			h.assertValueEqual(2, s.stock().items().size(), "stock kinds");
			StockLine logs = s.stock().items().get(0);
			h.assertValueEqual("minecraft:oak_log", logs.itemId(), "most plentiful first");
			h.assertValueEqual(64L, logs.count(), "logs");
			StockLine bread = s.stock().items().get(1);
			h.assertValueEqual(20L, bread.count(), "bread");
			h.assertValueEqual(5, bread.nutritionEach(), "bread nutrition");
			h.assertValueEqual(100L, s.stock().nutrition(), "nutrition");
			h.assertValueEqual(0, s.jobChoices().stream().filter(c -> c.jobId().equals("farmer")).findFirst().orElseThrow().freeSlots(), "farmer has no free slot");

			int before = dev.villageheart.heart.HeartFacts.collectCount;
			dev.villageheart.heart.HeartFacts.invalidate();
			dev.villageheart.heart.HeartFacts.cached(h.getLevel().getServer());
			dev.villageheart.heart.HeartFacts.cached(h.getLevel().getServer());
			h.assertValueEqual(before + 1, dev.villageheart.heart.HeartFacts.collectCount, "two viewers in one tick, one collect");
			done(h);
		});
	}
}
