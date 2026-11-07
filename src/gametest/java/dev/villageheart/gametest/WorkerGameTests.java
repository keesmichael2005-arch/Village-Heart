package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.*;

import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.ModProfessions;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.WorkerBrain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Layer 2: hiring and the worker framework (M7). Rules (docs/TESTING.md, "Worker GameTests"): one environment per test that touches
 * the claim or the clock, wait for POIs, schedule every callback from the test body (never runAfterDelay inside a callback), light the
 * area for night tests, clean up every spawned villager.
 */
public class WorkerGameTests {
	private static final double REACH = 1.5;

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	@GameTest
	public void professionsRegistered(GameTestHelper h) {
		for (JobType j : JobType.values()) {
			h.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.containsKey(ModProfessions.key(j)), "profession registered for " + j.id());
			h.assertValueEqual("villageheart:" + j.id(), ModProfessions.key(j).identifier().toString(), "key of " + j.id());
		}
		for (var poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE.listElements().toList()) {
			for (JobType j : JobType.values()) {
				var prof = ModProfessions.holder(j).value();
				h.assertFalse(prof.heldJobSite().test(poi), j.id() + " holds job site " + poi.getRegisteredName());
				h.assertFalse(prof.acquirableJobSite().test(poi), j.id() + " can acquire " + poi.getRegisteredName());
			}
		}
		for (JobType j : JobType.values()) {
			Villager v = h.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
			v.setVillagerData(v.getVillagerData().withProfession(ModProfessions.holder(j)));
			h.assertTrue(v.getOffers().isEmpty(), j.id() + " has no trades");
			v.discard();
		}
		h.succeed();
	}

	// ---- T-L2-2 (framework part, step 4): the worker follows WORK, not a stroll --------------------

	@GameTest(environment = "villageheart-gametest:workers_reach", structure = YARD, maxTicks = 3400)
	public void workerWalksToJobBlockInWorkHours(GameTestHelper h) {
		begin(h);
		floor(h, 0, 0, 24, 12);
		BlockPos block = new BlockPos(20, 1, 6);
		Villager[] v = new Villager[1];
		int[] near = {0};
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			h.setBlock(block, Blocks.COMPOSTER);
			h.getLevel().setDayTime(0);
			v[0] = spawnVillager(h, new BlockPos(3, 1, 6));
			hireDirect(h, v[0], JobType.FARMER, block);
			h.assertValueEqual("villageheart:farmer", v[0].getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString(), "profession");
			h.assertTrue(v[0].getOffers().isEmpty(), "no trades");
			h.assertTrue(WorkerAttachments.isWorker(v[0]), "attachment");
			h.assertTrue(WorkerBrain.isWorkerBrain(v[0]), "worker brain");
			h.assertTrue(distance(h, v[0], block) > 10, "starts far from the block");
		});
		// WAKE (day time 0 to 1000): the worker must not be at the block
		seq = seq.thenExecuteFor(900, () -> h.assertTrue(distance(h, v[0], block) > REACH, "reached the job block before work hours"));
		seq = seq.thenExecute(() -> h.assertTrue(h.getLevel().getDayTime() >= 850, "the day advanced: " + h.getLevel().getDayTime()));
		seq = seq.thenWaitUntil(() -> h.assertTrue(distance(h, v[0], block) <= REACH, "distance " + distance(h, v[0], block)));
		seq = seq.thenExecute(() -> {
			long day = h.getLevel().getDayTime();
			h.assertTrue(day >= 1000, "arrived at day time " + day + ", before work started");
			h.assertTrue(day <= 3000, "arrived at day time " + day + ", after 3000");
		});
		for (int i = 0; i < 10; i++) {
			seq = seq.thenExecuteAfter(100, () -> {
				if (distance(h, v[0], block) <= 2.0) near[0]++;
			});
		}
		seq.thenExecute(() -> {
			h.assertTrue(near[0] >= 8, "stayed at the job block in " + near[0] + " of 10 samples");
			end(h);
			h.succeed();
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:workers_door", structure = YARD, maxTicks = 800)
	public void workerOpensDoors(GameTestHelper h) {
		begin(h);
		floor(h, 0, 0, 14, 14);
		BlockPos block = new BlockPos(4, 1, 12);
		BlockPos doorPos = new BlockPos(4, 1, 7);
		Villager[] v = new Villager[1];
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			for (int x = 3; x <= 6; x++) for (int z = 3; z <= 7; z++) {
				boolean wall = x == 3 || x == 6 || z == 3 || z == 7;
				for (int y = 1; y <= 2; y++) if (wall) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
				h.setBlock(new BlockPos(x, 3, z), Blocks.OAK_PLANKS);
			}
			door(h, doorPos, Direction.NORTH);
			h.setBlock(block, Blocks.COMPOSTER);
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, new BlockPos(5, 1, 5));
			hireDirect(h, v[0], JobType.FARMER, block);
			h.assertFalse(h.getBlockState(doorPos).getValue(DoorBlock.OPEN), "the door starts closed");
			h.assertTrue(distance(h, v[0], block) > 5, "starts inside the cell, far from the block");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the job block through the closed door",
				() -> h.assertTrue(distance(h, v[0], block) <= REACH, "distance " + distance(h, v[0], block)));
		seq.thenExecute(() -> {
			h.assertTrue(h.getLevel().getDayTime() > 2000, "time advanced");
			end(h);
			h.succeed();
		});
	}

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:workers_schedule", structure = YARD, maxTicks = 1600)
	public void workerFollowsDaySchedule(GameTestHelper h) {
		begin(h);
		floor(h, 0, 0, 14, 14);
		light(h, 1, 1, 13, 13);
		BlockPos block = new BlockPos(10, 1, 3);
		BlockPos bed = new BlockPos(6, 1, 8);
		Villager[] v = new Villager[1];
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			h.setBlock(block, Blocks.COMPOSTER);
			bed(h, bed);
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, new BlockPos(3, 1, 3));
			hireDirect(h, v[0], JobType.FARMER, block);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getPoiManager().getType(h.absolutePos(bed.south())).isPresent(), "the bed head POI exists"));
		seq = seq.thenIdle(25).thenExecute(() -> {
			h.assertTrue(h.getLevel().getDayTime() > 2000, "time advanced: " + h.getLevel().getDayTime());
			h.assertTrue(v[0].getBrain().isActive(Activity.WORK), "WORK at day time " + h.getLevel().getDayTime());
			h.getLevel().setDayTime(11000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "go_home at 11000",
				() -> h.assertTrue(v[0].getBrain().isActive(WorkerBrain.GO_HOME), "go_home active"));
		seq = seq.thenExecute(() -> h.getLevel().setDayTime(13000));
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "asleep at 13000", () -> h.assertTrue(v[0].isSleeping(), "sleeping"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(v[0].getBrain().isActive(Activity.REST), "REST while asleep");
			h.assertTrue(v[0].getBrain().hasMemoryValue(MemoryModuleType.HOME), "home acquired");
			h.getLevel().setDayTime(1000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "awake and WORK at 1000", () -> {
			h.assertFalse(v[0].isSleeping(), "awake");
			h.assertTrue(v[0].getBrain().isActive(Activity.WORK), "WORK active");
		});
		seq.thenExecute(() -> {
			end(h);
			h.succeed();
		});
	}

	// ---- T-L2-9 (entity part) ------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:workers_reload", structure = YARD, maxTicks = 700)
	public void workerSurvivesReload(GameTestHelper h) {
		begin(h);
		floor(h, 0, 0, 14, 8);
		BlockPos block = new BlockPos(10, 1, 4);
		Villager[] a = new Villager[1];
		Villager[] b = new Villager[1];
		WorkerData[] data = new WorkerData[1];
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			h.setBlock(block, Blocks.COMPOSTER);
			h.getLevel().setDayTime(2000);
			a[0] = spawnVillager(h, new BlockPos(3, 1, 4));
			hireDirect(h, a[0], JobType.FARMER, block);
			data[0] = a[0].getAttached(WorkerAttachments.WORKER);
			h.assertTrue(data[0] != null, "attachment present");
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			a[0].saveWithoutId(out);
			CompoundTag tag = out.buildResult();
			a[0].discard();
			Entity e = EntityType.create(EntityType.VILLAGER, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag),
					level, EntitySpawnReason.LOAD).orElseThrow();
			level.addFreshEntity(e);
			b[0] = (Villager) e;
			track(b[0]);
			h.assertValueEqual(data[0], b[0].getAttached(WorkerAttachments.WORKER), "worker data after reload");
			h.assertValueEqual("villageheart:farmer", b[0].getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString(), "profession");
			h.assertTrue(b[0].getOffers().isEmpty(), "no trades");
			h.assertTrue(WorkerBrain.isWorkerBrain(b[0]), "worker brain after reload");
			h.assertTrue(b[0].getBrain().isActive(Activity.WORK), "WORK right after load");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the job block after the reload",
				() -> h.assertTrue(distance(h, b[0], block) <= REACH, "distance " + distance(h, b[0], block)));
		seq.thenExecute(() -> {
			end(h);
			h.succeed();
		});
	}

}
