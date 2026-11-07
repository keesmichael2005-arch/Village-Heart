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
import java.util.Optional;
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
			h.assertTrue(s.overview().alerts().isEmpty(), "no alerts: " + s.overview().alerts());

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
