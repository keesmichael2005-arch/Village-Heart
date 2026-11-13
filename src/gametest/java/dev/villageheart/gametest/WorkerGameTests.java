package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.*;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.worker.HireRefusal;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.SiteView;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.JobSiteSource;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
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
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

	/**
	 * Day time 0, the worker is shut into a pen with an iron door (villagers cannot open those) and the job block is 17 blocks
	 * away: during WAKE it is not working (activity IDLE, never a walk target on the block). When day time reaches 1000 the test opens
	 * the door; the worker must then be at the block within 400 ticks (a random stroll would not get there), before day time 3000,
	 * and stay there.
	 */
	@GameTest(environment = "villageheart-gametest:workers_reach", structure = YARD, maxTicks = 3400)
	public void workerWalksToJobBlockInWorkHours(GameTestHelper h) {
		begin(h);
		floor(h, 0, 0, 24, 12);
		BlockPos block = new BlockPos(20, 1, 6);
		BlockPos gate = new BlockPos(6, 1, 6);
		Villager[] v = new Villager[1];
		int[] near = {0};
		long[] opened = {0};
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			for (int x = 2; x <= 6; x++) for (int z = 4; z <= 8; z++) {
				boolean wall = x == 2 || x == 6 || z == 4 || z == 8;
				for (int y = 1; y <= 2; y++) if (wall) h.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS);
				h.setBlock(new BlockPos(x, 3, z), Blocks.OAK_PLANKS);
			}
			BlockState iron = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
			h.setBlock(gate, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
			h.setBlock(gate.above(), iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
			h.setBlock(block, Blocks.COMPOSTER);
			h.getLevel().setDayTime(0);
			v[0] = spawnVillager(h, new BlockPos(4, 1, 6));
			hireDirect(h, v[0], JobType.FARMER, block);
			h.assertValueEqual("villageheart:farmer", v[0].getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString(), "profession");
			h.assertTrue(v[0].getOffers().isEmpty(), "no trades");
			h.assertTrue(WorkerAttachments.isWorker(v[0]), "attachment");
			h.assertTrue(WorkerBrain.isWorkerBrain(v[0]), "worker brain");
			h.assertTrue(distance(h, v[0], block) > 10, "starts far from the block");
		});
		// WAKE: not working, never heading for the job block
		seq = seq.thenExecuteFor(900, () -> {
			h.assertFalse(v[0].getBrain().isActive(Activity.WORK), "WORK before work hours, day time " + h.getLevel().getDayTime());
			var target = v[0].getBrain().getMemory(MemoryModuleType.WALK_TARGET);
			h.assertFalse(target.isPresent() && target.get().getTarget().currentBlockPosition().equals(h.absolutePos(block)),
					"walking to the job block before work hours");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 1000, "waiting for work hours: " + h.getLevel().getDayTime()));
		seq = seq.thenExecute(() -> {
			opened[0] = h.getLevel().getDayTime();
			BlockState open = h.getBlockState(gate).setValue(DoorBlock.OPEN, true);
			h.setBlock(gate, open);
			h.setBlock(gate.above(), h.getBlockState(gate.above()).setValue(DoorBlock.OPEN, true));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the job block after work started",
				() -> h.assertTrue(distance(h, v[0], block) <= REACH, "distance " + distance(h, v[0], block)));
		seq = seq.thenExecute(() -> {
			long day = h.getLevel().getDayTime();
			h.assertTrue(day >= 1000 && day <= 3000, "arrived at day time " + day + " (opened at " + opened[0] + ")");
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

	/** Other living entities within 8 blocks, for walk traces: {@code " zombie@x,y,z"} each. */
	private static String near(GameTestHelper h, Villager v) {
		StringBuilder sb = new StringBuilder();
		for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, v.getBoundingBox().inflate(8), e -> e != v)) {
			sb.append(' ').append(e.getType().toShortString()).append('@').append(VillageTestSupport.rel(h, e.blockPosition()).toShortString());
		}
		return sb.toString();
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
		// walk-time log (BACKLOG: slow outliers of 301 and 456 ticks): a trace every 20 ticks, printed with the walk time
		List<String> walkTrace = new ArrayList<>();
		long[] t0 = {-1};
		h.onEachTick(() -> {
			if (v[0] == null || t0[0] < 0 || (h.getTick() - t0[0]) % 20 != 0 || walkTrace.size() > 40) return;
			var brain = v[0].getBrain();
			walkTrace.add((h.getTick() - t0[0]) + ":" + VillageTestSupport.rel(h, v[0].blockPosition()).toShortString() + (h.getBlockState(doorPos).getValue(DoorBlock.OPEN) ? " open" : " shut")
					+ " " + brain.getActiveNonCoreActivity().map(Object::toString).orElse("-")
					+ (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET) ? " wt" : "")
					+ (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH) ? " path" : "")
					+ (brain.hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE) ? " cantReach" : "")
					+ near(h, v[0]));
		});
		seq = seq.thenExecute(() -> t0[0] = h.getTick());
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the job block through the closed door",
				() -> h.assertTrue(distance(h, v[0], block) <= REACH, "distance " + distance(h, v[0], block) + "; trace " + walkTrace));
		seq.thenExecute(() -> {
			System.out.println("[m7] workerOpensDoors: at the job block after " + (h.getTick() - t0[0]) + " ticks; trace " + walkTrace);
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

	// ---- reservations (step 5) -----------------------------------------------------------------

	private static void breakMarker(GameTestHelper h, BlockPos marker) {
		ServerPlayer breaker = BuildingTestSupport.owner(); // M24: Visitors cannot break registered markers
		breaker.gameMode.destroyBlock(h.absolutePos(marker));
	}

	/** T-L2-5 (roadmap): a reserved composter is not taken for a whole day; with the marker removed (control) it is. */
	@GameTest(environment = "villageheart-gametest:workers_reserved", structure = YARD, skyAccess = true, maxTicks = 28000)
	public void reservedComposterNotTaken(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		Villager[] v = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID);
			h.assertTrue(poiPresent(h, comp), "the composter's POI exists");
			h.assertTrue(isHeld(h, comp), "the composter is reserved");
			h.assertFalse(poiHasSpace(h, comp), "the reserved composter has no free ticket");
		});
		seq = seq.thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 1, 0, 1));
		});
		for (int i = 0; i < 120; i++) {
			seq = seq.thenExecuteAfter(200, () -> {
				h.assertTrue(v[0].isAlive() && !v[0].isBaby(), "the villager is alive and adult");
				h.assertTrue(v[0].distanceToSqr(h.absoluteVec(Vec3.atBottomCenterOf(comp))) < 44 * 44, "the villager is within the job-site search range (48) of the composter");
				h.assertTrue(isUnemployed(v[0]), "the villager is unemployed, not " + v[0].getVillagerData().profession().getRegisteredName());
				h.assertFalse(claims(h, v[0], comp), "the villager does not claim the reserved composter");
				h.assertTrue(isHeld(h, comp), "still reserved");
			});
		}
		// control, same layout: the marker is removed, the building is unregistered, the composter is free for vanilla again
		seq = seq.thenExecute(() -> breakMarker(h, marker));
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the villager takes the freed composter", () -> {
			h.assertFalse(isHeld(h, comp), "released");
			h.assertTrue(isFarmer(v[0]), "farmer, not " + v[0].getVillagerData().profession().getRegisteredName());
		});
		seq.thenExecute(() -> {
			h.assertTrue(poiHasSpace(h, comp) || claims(h, v[0], comp), "the POI is free or claimed by the villager");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-5b (roadmap "vanilla untouched"): the same composter outside any building is taken as always. */
	@GameTest(environment = "villageheart-gametest:workers_reserved_free", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void freeComposterTakenOutsideBuilding(GameTestHelper h) {
		begin(h);
		floor(h, 8, 4, 16, 12);
		BlockPos comp = new BlockPos(12, 1, 8);
		Villager[] v = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> h.setBlock(comp, Blocks.COMPOSTER));
		seq = seq.thenWaitUntil(() -> h.assertTrue(poiPresent(h, comp), "the composter's POI exists"));
		seq = seq.thenExecute(() -> {
			h.assertFalse(isHeld(h, comp), "not reserved: no building");
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, new BlockPos(11, 1, 8));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the villager becomes a farmer",
				() -> h.assertTrue(isFarmer(v[0]), "profession " + v[0].getVillagerData().profession().getRegisteredName() + ", day time " + h.getLevel().getDayTime() + ", job site " + v[0].getBrain().getMemory(MemoryModuleType.JOB_SITE) + ", potential " + v[0].getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE) + ", poi space " + poiHasSpace(h, comp) + ", at " + v[0].blockPosition()));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-5c: a vanilla farmer already working a composter loses it when a valid building forms around it. */
	@GameTest(environment = "villageheart-gametest:workers_reserved_evict", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void existingVanillaHolderEvicted(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		Villager[] v = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1); // no marker yet: not a building
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 1, 0, 1));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(poiPresent(h, comp), "the composter's POI exists"));
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the villager becomes a farmer at the composter", () -> {
			h.assertTrue(isFarmer(v[0]), "farmer");
			h.assertTrue(claims(h, v[0], comp), "claims the composter");
		});
		seq = seq.thenExecute(() -> {
			h.assertFalse(poiHasSpace(h, comp), "the farmer holds the composter's ticket");
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID));
		seq = BuildingTestSupport.awaitWithin(h, seq, 60, "the vanilla holder is evicted", () -> {
			h.assertFalse(claims(h, v[0], comp), "the farmer's job site is erased");
			h.assertTrue(isHeld(h, comp), "reserved");
			h.assertFalse(poiHasSpace(h, comp), "no free ticket (one ticket, not two)");
		});
		seq = seq.thenIdle(400).thenExecute(() -> {
			h.assertFalse(claims(h, v[0], comp), "still not claiming");
			h.assertTrue(isUnemployed(v[0]), "profession back to none, not " + v[0].getVillagerData().profession().getRegisteredName());
			h.assertTrue(isHeld(h, comp) && !poiHasSpace(h, comp), "still reserved");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-5d: every mod-job workstation of a valid building is reserved, slot or not; blocks without a POI need nothing. */
	@GameTest(environment = "villageheart-gametest:workers_reserved_extra", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void extraJobBlocksReserved(GameTestHelper h) {
		begin(h);
		BlockPos farmMarker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos campMarker = BuildingTestSupport.markerPos(ORIGIN_B, 5);
		BlockPos c1 = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0), c2 = BuildingTestSupport.inside(ORIGIN_A, 4, 0, 0);
		BlockPos smoker = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 3);
		BlockPos chop = BuildingTestSupport.inside(ORIGIN_B, 0, 0, 0);
		Villager[] v = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 2);
			h.setBlock(smoker, Blocks.SMOKER);
			BuildingTestSupport.room(h, ORIGIN_B, 5, 4);
			h.setBlock(chop, JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
			h.setBlock(BuildingTestSupport.inside(ORIGIN_B, 4, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(ORIGIN_B, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, farmMarker, Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.LUMBER_CAMP, campMarker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, farmMarker, MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, campMarker, MarkerStatus.VALID);
			h.assertTrue(poiPresent(h, c1) && poiPresent(h, c2) && poiPresent(h, smoker), "the POIs exist");
			h.assertTrue(isHeld(h, c1) && isHeld(h, c2) && isHeld(h, smoker), "both composters and the smoker are reserved");
		});
		seq = seq.thenExecute(() -> {
			h.assertFalse(isHeld(h, chop), "nothing is held for the Chopping Block (it has no POI)");
			h.assertValueEqual(3, VillageClaims.data(h.getLevel().getServer()).reservedJobBlocks().size(), "exactly three held blocks");
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 1, 0, 1));
		});
		seq = seq.thenIdle(1200).thenExecute(() -> {
			h.assertTrue(isUnemployed(v[0]), "still unemployed next to three workstations");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-5e (1): a held block that is broken drops out of the set; placed again it is reserved once its POI is back. */
	@GameTest(environment = "villageheart-gametest:workers_reserved_replaced", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void replacedBlockReserved(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(isHeld(h, comp) && !poiHasSpace(h, comp), "reserved"));
		seq = seq.thenExecute(() -> h.destroyBlock(comp));
		// 1 building: its re-check (20 ticks) then the pass (20 ticks), plus the deferred POI removal and slack
		seq = BuildingTestSupport.awaitWithin(h, seq, 60, "the broken block leaves the held set", () -> h.assertFalse(isHeld(h, comp), "still held"));
		seq = seq.thenExecute(() -> h.setBlock(comp, Blocks.COMPOSTER));
		seq = BuildingTestSupport.awaitWithin(h, seq, 64, "the replaced composter is reserved again", () -> {
			h.assertTrue(poiPresent(h, comp), "POI back");
			h.assertTrue(isHeld(h, comp), "held again");
			h.assertFalse(poiHasSpace(h, comp), "the new POI record has no free ticket");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-5e (2): the pass never loads chunks; a held position in an unloaded chunk stays held and the chunk stays unloaded. */
	@GameTest(environment = "villageheart-gametest:workers_reserved_unloaded", structure = YARD, maxTicks = 300)
	public void reservationPassNeverLoadsChunks(GameTestHelper h) {
		begin(h);
		HeartLocation[] far = new HeartLocation[1];
		JobSiteSource[] previous = new JobSiteSource[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			HeartLocation heart = VillageClaims.current(server).orElseThrow().heart();
			far[0] = new HeartLocation(heart.dimension(), heart.x(), heart.y(), heart.z() + 100_000);
			h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far[0].x() >> 4, far[0].z() >> 4) == null, "the far chunk starts unloaded");
			previous[0] = Workers.setJobSiteSourceForTest(new JobSiteSource() {
				@Override public List<SiteView> sites(MinecraftServer s, JobType job) { return List.of(); }
				@Override public boolean isRegistered(MinecraftServer s, HeartLocation b) { return true; }
				@Override public List<HeartLocation> reservable(MinecraftServer s) { return List.of(far[0]); }
				@Override public boolean contentsPending(MinecraftServer s) { return false; }
			});
			VillageClaims.data(server).setReservedJobBlocks(Set.of(far[0]));
		});
		seq = seq.thenIdle(70).thenExecute(() -> {
			MinecraftServer server = h.getLevel().getServer();
			h.assertValueEqual(Set.of(far[0]), VillageClaims.data(server).reservedJobBlocks(), "the unloaded position stays held");
			h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far[0].x() >> 4, far[0].z() >> 4) == null, "the pass did not load the chunk");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-9 (village part): the held set is saved with the village and read back. */
	@GameTest(environment = "villageheart-gametest:workers_reserved_persist", structure = YARD, skyAccess = true, maxTicks = 600)
	public void heldBlocksAreSaved(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(isHeld(h, comp), "reserved"));
		seq.thenExecute(() -> {
			try {
				var onDisk = VillageTestSupport.reload(h.getLevel().getServer());
				h.assertTrue(onDisk.reservedJobBlocks().contains(loc(h, comp)), "the saved data holds the composter: " + onDisk.reservedJobBlocks());
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- hire and fire (step 6) ----------------------------------------------------------------

	private static final BlockPos ORIGIN_NORTH = new BlockPos(8, 0, 10);

	private static HireResult hire(GameTestHelper h, net.minecraft.world.entity.Entity e, JobType job) {
		return Workers.hire(h.getLevel(), e, job, java.util.Optional.empty());
	}

	private static void assertRefused(GameTestHelper h, HireResult r, HireRefusal expected, String what) {
		h.assertTrue(r instanceof HireResult.Refused, what + ": refused, got " + r);
		h.assertValueEqual(expected, ((HireResult.Refused) r).reason(), what);
	}

	private static java.util.Optional<dev.villageheart.core.WorkerRecord> record(GameTestHelper h, Villager v) {
		return VillageClaims.current(h.getLevel().getServer()).flatMap(s -> s.worker(v.getUUID()));
	}

	/**
	 * T-L2-2 (roadmap): hired through the real path into a real Farmhouse. The Farmhouse has an iron door that the test opens at day
	 * time 1000, so "not before work hours" is exact; the worker then reaches the composter well before 3000.
	 */
	@GameTest(environment = "villageheart-gametest:workers_hired", structure = YARD, skyAccess = true, maxTicks = 4400)
	public void hiredWorkerReachesJobBlock(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_NORTH, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_NORTH, 0, 0, 0);
		BlockPos[] door = new BlockPos[1];
		Villager[] v = new Villager[1];
		int[] near = {0};
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			floor(h, 4, 0, 20, 9);
			farmhouse(h, ORIGIN_NORTH, 1);
			door[0] = ironDoor(h, ORIGIN_NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID);
			h.assertTrue(isHeld(h, comp), "the composter is reserved");
		});
		seq = seq.thenExecute(() -> {
			h.getLevel().setDayTime(0);
			v[0] = spawnVillager(h, new BlockPos(11, 1, 4));
			HireResult r = hire(h, v[0], JobType.FARMER);
			h.assertTrue(r instanceof HireResult.Hired, "hired: " + r);
			h.assertValueEqual("villageheart:farmer", v[0].getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString(), "profession");
			h.assertTrue(v[0].getOffers().isEmpty(), "no trades");
			h.assertTrue(WorkerAttachments.isWorker(v[0]), "attachment");
			var rec = record(h, v[0]);
			h.assertTrue(rec.isPresent(), "record present");
			h.assertValueEqual(loc(h, comp), rec.get().jobBlock(), "the record's job block is the composter");
			h.assertValueEqual(loc(h, marker), rec.get().building(), "the record's building is the Farmhouse marker");
		});
		seq = seq.thenExecuteFor(900, () -> {
			h.assertFalse(v[0].getBrain().isActive(Activity.WORK), "WORK before work hours, day time " + h.getLevel().getDayTime());
			h.assertTrue(distance(h, v[0], comp) > REACH, "at the composter before work hours");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 1000, "waiting for work hours"));
		seq = seq.thenExecute(() -> openDoor(h, door[0]));
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the composter after work started",
				() -> h.assertTrue(distance(h, v[0], comp) <= REACH, "distance " + distance(h, v[0], comp)));
		seq = seq.thenExecute(() -> {
			long day = h.getLevel().getDayTime();
			h.assertTrue(day >= 1000 && day <= 3000, "arrived at day time " + day);
		});
		for (int i = 0; i < 10; i++) {
			seq = seq.thenExecuteAfter(100, () -> {
				if (distance(h, v[0], comp) <= 2.0) near[0]++;
			});
		}
		seq.thenExecute(() -> {
			h.assertTrue(near[0] >= 8, "stayed at the composter in " + near[0] + " of 10 samples");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-6: refusals, through the API and through the command. */
	@GameTest(environment = "villageheart-gametest:workers_refusals", structure = YARD, skyAccess = true, maxTicks = 600)
	public void hireRefusedWithoutFreeSlot(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID);
			h.assertTrue(isHeld(h, comp), "reserved");
		});
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			MinecraftServer server = level.getServer();
			BlockPos spot = BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1);
			Villager a = spawnVillager(h, spot), b = spawnVillager(h, spot), baby = spawnVillager(h, spot);
			Villager nitwit = spawnVillager(h, spot), traded = spawnVillager(h, spot);
			baby.setAge(-24000);
			nitwit.setVillagerData(nitwit.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NITWIT));

			h.assertTrue(hire(h, a, JobType.FARMER) instanceof HireResult.Hired, "A hired");
			assertRefused(h, hire(h, b, JobType.FARMER), HireRefusal.NO_FREE_SLOT, "B has no slot");
			h.assertTrue(isUnemployed(b) && !WorkerAttachments.isWorker(b) && record(h, b).isEmpty(), "B unchanged");
			assertRefused(h, hire(h, a, JobType.FARMER), HireRefusal.ALREADY_EMPLOYED, "A again");
			assertRefused(h, hire(h, baby, JobType.FARMER), HireRefusal.NOT_ADULT, "baby");
			assertRefused(h, hire(h, nitwit, JobType.FARMER), HireRefusal.NITWIT, "nitwit");
			assertRefused(h, Workers.hire(level, a, JobType.MINER, java.util.Optional.empty()), HireRefusal.ALREADY_EMPLOYED, "employed asks for a tier-locked job");
			assertRefused(h, hire(h, b, JobType.MINER), HireRefusal.TIER_LOCKED, "Miner at Hamlet");
			net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader trader = h.spawn(EntityType.WANDERING_TRADER, spot);
			assertRefused(h, hire(h, trader, JobType.FARMER), HireRefusal.NOT_A_VILLAGER, "wandering trader");

			var source = server.createCommandSourceStack().withLevel(level);
			server.getCommands().performPrefixedCommand(source, "villageheart hire " + trader.getUUID() + " farmer");
			h.assertFalse(WorkerAttachments.isWorker(trader), "the command did not make a trader a worker");
			server.getCommands().performPrefixedCommand(source, "villageheart hire " + b.getUUID() + " wizard");
			h.assertTrue(record(h, b).isEmpty(), "unknown job: nothing happened");

			// firing frees the slot; a villager that has traded is hired with xp 0 and no trades
			server.getCommands().performPrefixedCommand(source, "villageheart fire " + a.getUUID());
			h.assertTrue(record(h, a).isEmpty() && !WorkerAttachments.isWorker(a) && isUnemployed(a), "A fired through the command");
			traded.setVillagerData(traded.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
			traded.setVillagerXp(10);
			h.assertFalse(traded.getOffers().isEmpty(), "the traded villager has trades before it is hired");
			server.getCommands().performPrefixedCommand(source, "villageheart hire " + traded.getUUID() + " farmer");
			h.assertTrue(record(h, traded).isPresent(), "hired through the command");
			h.assertValueEqual(0, traded.getVillagerXp(), "xp");
			h.assertTrue(traded.getOffers().isEmpty(), "trades are gone");
			h.assertTrue(WorkerAttachments.isWorker(traded), "attachment");
			assertRefused(h, hire(h, a, JobType.FARMER), HireRefusal.NO_FREE_SLOT, "the slot is the traded villager's now");
			h.assertValueEqual(1, VillageClaims.current(server).orElseThrow().workers().size(), "one record");
			trader.discard();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-7 (roadmap): firing restores a vanilla villager; the slot is free; the vanilla brain takes a free composter again. */
	@GameTest(environment = "villageheart-gametest:workers_fire", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void firingRestoresVanilla(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		BlockPos free = new BlockPos(26, 1, 12);
		Villager[] a = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			floor(h, 22, 8, 30, 16);
			h.setBlock(free, Blocks.COMPOSTER);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID);
			h.assertTrue(isHeld(h, comp) && poiPresent(h, free), "reserved, and the free composter has its POI");
		});
		seq = seq.thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			a[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, a[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
			h.assertTrue(Workers.fire(h.getLevel(), a[0]), "fired");
			h.assertTrue(isUnemployed(a[0]), "profession none");
			h.assertFalse(WorkerAttachments.isWorker(a[0]), "no attachment");
			h.assertFalse(WorkerBrain.isWorkerBrain(a[0]), "vanilla brain");
			h.assertTrue(record(h, a[0]).isEmpty(), "no record");
			Villager b = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 3, 0, 1));
			h.assertTrue(hire(h, b, JobType.FARMER) instanceof HireResult.Hired, "the slot is free again: B hired");
			Workers.fire(h.getLevel(), b);
			b.discard();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the fired villager takes the free composter", () -> {
			h.assertTrue(isFarmer(a[0]), "profession " + a[0].getVillagerData().profession().getRegisteredName());
			h.assertTrue(claims(h, a[0], free), "claims the free composter");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- lifecycle (step 7) --------------------------------------------------------------------

	/** Saves {@code v} to NBT, discards it and loads a copy (the spike recipe for "the chunk unloaded and loaded again"). */
	private static Villager reloadEntity(ServerLevel level, Villager v) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		v.saveWithoutId(out);
		CompoundTag tag = out.buildResult();
		v.discard();
		Entity e = EntityType.create(EntityType.VILLAGER, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag),
				level, EntitySpawnReason.LOAD).orElseThrow();
		level.addFreshEntity(e);
		track((Villager) e);
		return (Villager) e;
	}

	/** Builds a valid Farmhouse (1 composter), waits until it is valid and reserved, then continues with {@code next}. */
	private static GameTestSequence validFarmhouse(GameTestHelper h, BlockPos origin) {
		BlockPos marker = BuildingTestSupport.markerPos(origin, 5);
		BlockPos comp = BuildingTestSupport.inside(origin, 0, 0, 0);
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, origin, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, marker, Direction.NORTH);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID);
			h.assertTrue(isHeld(h, comp) && !poiHasSpace(h, comp), "the composter is reserved");
		});
	}

	/** T-L2-9 (roadmap): a worker survives an entity reload and a village reload, with its reservation. */
	@GameTest(environment = "villageheart-gametest:workers_reload", structure = YARD, skyAccess = true, maxTicks = 900)
	public void workerSurvivesReload(GameTestHelper h) {
		begin(h);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		Villager[] a = new Villager[1];
		Villager[] b = new Villager[1];
		WorkerData[] data = new WorkerData[1];
		GameTestSequence seq = validFarmhouse(h, ORIGIN_A).thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			a[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, a[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
			data[0] = a[0].getAttached(WorkerAttachments.WORKER);
			h.assertTrue(data[0] != null, "attachment present");
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			b[0] = reloadEntity(level, a[0]);
			h.assertValueEqual(data[0], b[0].getAttached(WorkerAttachments.WORKER), "worker data after reload");
			h.assertValueEqual("villageheart:farmer", b[0].getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString(), "profession");
			h.assertTrue(b[0].getOffers().isEmpty(), "no trades");
			h.assertTrue(WorkerBrain.isWorkerBrain(b[0]), "worker brain after reload");
			h.assertTrue(b[0].getBrain().isActive(Activity.WORK), "WORK right after load");
			h.assertFalse(poiHasSpace(h, comp), "the composter's POI still has no free ticket after the entity reload");
		});
		seq = seq.thenIdle(45).thenExecute(() -> {
			h.assertTrue(WorkerAttachments.isWorker(b[0]), "the reloaded worker is still a worker 45 ticks later (not treated as an orphan)");
			h.assertTrue(record(h, b[0]).isPresent(), "its record is still there");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the job block after the reload",
				() -> h.assertTrue(distance(h, b[0], comp) <= REACH, "distance " + distance(h, b[0], comp)));
		seq.thenExecute(() -> {
			try {
				var onDisk = VillageTestSupport.reload(h.getLevel().getServer());
				var live = VillageClaims.current(h.getLevel().getServer()).orElseThrow();
				h.assertValueEqual(live.workers(), onDisk.village().orElseThrow().workers(), "saved workers equal the live ones");
				h.assertTrue(onDisk.reservedJobBlocks().contains(loc(h, comp)), "the saved reservations hold the composter");
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-8: death and zombie conversion free the slot and never leave a mod profession on the zombie, in either event order. */
	@GameTest(environment = "villageheart-gametest:workers_death", structure = YARD, skyAccess = true, maxTicks = 600)
	public void workerDeathAndConversion(GameTestHelper h) {
		begin(h);
		GameTestSequence seq = validFarmhouse(h, ORIGIN_A);
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			MinecraftServer server = level.getServer();
			BlockPos spot = BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1);
			net.minecraft.world.entity.monster.zombie.Zombie zombie = h.spawn(EntityType.ZOMBIE, spot);
			zombie.setNoAi(true);

			// death
			Villager a = spawnVillager(h, spot);
			h.assertTrue(hire(h, a, JobType.FARMER) instanceof HireResult.Hired, "A hired");
			for (int i = 0; i < 5 && a.isAlive(); i++) a.hurtServer(level, level.damageSources().genericKill(), 1000.0F);
			h.assertFalse(a.isAlive(), "A is dead");
			h.assertTrue(record(h, a).isEmpty(), "death removed the record");

			// positive controls: the conversion path copies VillagerData
			Villager farmer = spawnVillager(h, spot);
			farmer.setVillagerData(farmer.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER));
			Villager modNoAttachment = spawnVillager(h, spot);
			modNoAttachment.setVillagerData(modNoAttachment.getVillagerData().withProfession(ModProfessions.holder(JobType.FARMER)));
			h.assertTrue(zombie.convertVillagerToZombieVillager(level, farmer), "control conversion 1");
			h.assertTrue(zombie.convertVillagerToZombieVillager(level, modNoAttachment), "control conversion 2");
			var zombies = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.ZombieVillager.class, new net.minecraft.world.phys.AABB(h.absolutePos(spot)).inflate(3));
			h.assertValueEqual(2, zombies.size(), "two zombie villagers from the controls");
			boolean sawFarmer = false;
			for (var z : zombies) {
				if (z.getVillagerData().profession().is(VillagerProfession.FARMER)) sawFarmer = true;
				else h.assertTrue(z.getVillagerData().profession().is(VillagerProfession.NONE), "a mod profession survived the conversion: "
						+ z.getVillagerData().profession().getRegisteredName());
				z.discard();
			}
			h.assertTrue(sawFarmer, "a vanilla farmer keeps its profession (so the path copies VillagerData)");

			// B: hired, converted by the real path
			Villager b = spawnVillager(h, spot);
			h.assertTrue(hire(h, b, JobType.FARMER) instanceof HireResult.Hired, "the slot was free again: B hired");
			h.assertTrue(zombie.convertVillagerToZombieVillager(level, b), "conversion of B");
			h.assertTrue(VillageClaims.current(server).orElseThrow().workers().isEmpty(), "conversion removed the record: slot free");
			zombies = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.ZombieVillager.class, new net.minecraft.world.phys.AABB(h.absolutePos(spot)).inflate(3));
			h.assertValueEqual(1, zombies.size(), "one zombie villager from B");
			h.assertTrue(zombies.get(0).getVillagerData().profession().is(VillagerProfession.NONE), "zombie profession is none, not "
					+ zombies.get(0).getVillagerData().profession().getRegisteredName());
			zombies.get(0).discard();

			// C: the record is already gone when the conversion happens (as if the death/convert handler ran first)
			Villager c = spawnVillager(h, spot);
			h.assertTrue(hire(h, c, JobType.FARMER) instanceof HireResult.Hired, "C hired");
			Workers.fireById(server, c.getUUID());
			h.assertTrue(WorkerAttachments.isWorker(c), "C still carries the attachment");
			h.assertTrue(zombie.convertVillagerToZombieVillager(level, c), "conversion of C");
			zombies = level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.ZombieVillager.class, new net.minecraft.world.phys.AABB(h.absolutePos(spot)).inflate(3));
			h.assertValueEqual(1, zombies.size(), "one zombie villager from C");
			h.assertTrue(zombies.get(0).getVillagerData().profession().is(VillagerProfession.NONE), "zombie profession is none (record removed first)");
			zombies.get(0).discard();
			zombie.discard();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-10: a villager that carries worker data but has no record is restored to vanilla when it loads. */
	@GameTest(environment = "villageheart-gametest:workers_orphan", structure = YARD, maxTicks = 300)
	public void orphanAttachmentRestored(GameTestHelper h) {
		begin(h);
		BlockPos block = new BlockPos(10, 1, 4);
		Villager[] v = new Villager[1];
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			floor(h, 0, 0, 14, 8);
			v[0] = spawnVillager(h, new BlockPos(3, 1, 4));
			dev.villageheart.worker.WorkerSystem.exemptFromOrphanSweepForTest(v[0].getUUID()); // simulate "fired while unloaded"
			Workers.install(h.getLevel(), v[0], WorkerData.hired(JobType.FARMER, loc(h, block), loc(h, block)));
			h.assertTrue(WorkerAttachments.isWorker(v[0]) && !isUnemployed(v[0]), "a worker with no record");
		});
		seq = seq.thenIdle(3).thenExecute(() -> {
			Villager copy = reloadEntity(h.getLevel(), v[0]);
			dev.villageheart.worker.WorkerSystem.clearOrphanExemptionsForTest();
			v[0] = copy;
			// the copy was created with the attachment and the mod profession; the load handler runs at the end of this tick
			h.assertTrue(WorkerAttachments.isWorker(copy), "the copy carries the attachment right after loading");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the orphan is restored", () -> {
			h.assertFalse(WorkerAttachments.isWorker(v[0]), "attachment gone");
			h.assertTrue(isUnemployed(v[0]), "profession none, not " + v[0].getVillagerData().profession().getRegisteredName());
			h.assertFalse(WorkerBrain.isWorkerBrain(v[0]), "vanilla brain");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-11: releasing the village (breaking the Heart) frees every reserved block and every worker. */
	@GameTest(environment = "villageheart-gametest:workers_release", structure = YARD, skyAccess = true, maxTicks = 600)
	public void heartReleaseFreesEverything(GameTestHelper h) {
		begin(h);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		Villager[] a = new Villager[1];
		GameTestSequence seq = validFarmhouse(h, ORIGIN_A).thenExecute(() -> {
			a[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, a[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
			BuildingTestSupport.owner().gameMode.destroyBlock(h.absolutePos(new BlockPos(3, 1, 1)));
			h.assertTrue(VillageClaims.current(h.getLevel().getServer()).isEmpty(), "released");
			h.assertFalse(WorkerAttachments.isWorker(a[0]), "the worker is a vanilla villager again");
			h.assertTrue(isUnemployed(a[0]), "profession none");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the composter's ticket is free again", () -> h.assertTrue(poiHasSpace(h, comp), "POI has space"));
		seq.thenExecute(() -> {
			h.assertTrue(VillageClaims.data(h.getLevel().getServer()).reservedJobBlocks().isEmpty(), "no reservations left");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-15: removing the building's marker fires its workers. */
	@GameTest(environment = "villageheart-gametest:workers_marker_removed", structure = YARD, skyAccess = true, maxTicks = 600)
	public void markerRemovedFiresWorker(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		Villager[] a = new Villager[1];
		GameTestSequence seq = validFarmhouse(h, ORIGIN_A).thenExecute(() -> {
			a[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, a[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
			breakMarker(h, marker);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the worker is fired after the marker is gone", () -> {
			h.assertTrue(record(h, a[0]).isEmpty(), "record gone");
			h.assertFalse(WorkerAttachments.isWorker(a[0]), "attachment gone");
			h.assertTrue(isUnemployed(a[0]), "profession none");
			h.assertFalse(isHeld(h, comp), "composter not held");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-16: an invalid (but still registered) building keeps its workers; the reservation is released until it is valid again. */
	@GameTest(environment = "villageheart-gametest:workers_invalid", structure = YARD, skyAccess = true, maxTicks = 800)
	public void invalidBuildingKeepsWorker(GameTestHelper h) {
		begin(h);
		BlockPos marker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		BlockPos roof = ORIGIN_A.offset(3, 4, 2);
		Villager[] a = new Villager[1];
		dev.villageheart.core.WorkerRecord[] rec = new dev.villageheart.core.WorkerRecord[1];
		GameTestSequence seq = validFarmhouse(h, ORIGIN_A).thenExecute(() -> {
			a[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, a[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
			rec[0] = record(h, a[0]).orElseThrow();
			// a farmer with no farmland in reach is "No work" (M10); the test is about the building, so give it a field
			FarmTestSupport.emptyFarmland(h, 20, 8);
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.OK, Workers.status(h.getLevel().getServer(), rec[0]), "status while valid");
			h.destroyBlock(roof);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marker, MarkerStatus.INVALID));
		seq = BuildingTestSupport.awaitWithin(h, seq, 60, "the reservation is released while invalid", () -> {
			h.assertFalse(isHeld(h, comp), "composter not held");
			h.assertTrue(poiHasSpace(h, comp), "composter free");
		});
		seq = seq.thenIdle(45).thenExecute(() -> {
			h.assertValueEqual(java.util.Optional.of(rec[0]), record(h, a[0]), "the worker keeps its record");
			h.assertTrue(WorkerAttachments.isWorker(a[0]) && !isUnemployed(a[0]), "attachment and mod profession kept");
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.WORKPLACE_INVALID, Workers.status(h.getLevel().getServer(), rec[0]), "status while invalid");
			h.setBlock(roof, Blocks.OAK_PLANKS);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marker, MarkerStatus.VALID));
		seq = BuildingTestSupport.awaitWithin(h, seq, 60, "reserved again after the roof is fixed", () -> {
			h.assertTrue(isHeld(h, comp) && !poiHasSpace(h, comp), "composter reserved again");
		});
		seq.thenExecute(() -> {
			h.assertValueEqual(java.util.Optional.of(rec[0]), record(h, a[0]), "same record, same slot");
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.OK, Workers.status(h.getLevel().getServer(), rec[0]), "status after the fix");
			FarmTestSupport.clearStrayFarmland(h, 40); // the field of this test must not stay for the next one
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-17: a worker running a vanilla brain gets the worker brain back; a returning vanilla holder of a reserved block loses it. */
	@GameTest(environment = "villageheart-gametest:workers_heal", structure = YARD, skyAccess = true, maxTicks = 700)
	public void brainSelfHealAndLoadEviction(GameTestHelper h) {
		begin(h);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		Villager[] a = new Villager[1];
		Villager[] holder = new Villager[1];
		GameTestSequence seq = validFarmhouse(h, ORIGIN_A).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			level.setDayTime(2000);
			a[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, a[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
			WorkerData data = a[0].getAttached(WorkerAttachments.WORKER);
			// a vanilla brain on a worker: refresh with the attachment removed (the mixin does not fire), then put the attachment back
			a[0].removeAttached(WorkerAttachments.WORKER);
			a[0].refreshBrain(level);
			a[0].setAttached(WorkerAttachments.WORKER, data);
			h.assertFalse(WorkerBrain.isWorkerBrain(a[0]), "the villager runs a vanilla brain");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the worker brain is back", () -> {
			h.assertTrue(WorkerBrain.isWorkerBrain(a[0]), "worker brain");
			h.assertTrue(a[0].getVillagerData().profession().equals(ModProfessions.holder(JobType.FARMER)), "mod profession "
					+ a[0].getVillagerData().profession().getRegisteredName());
			h.assertTrue(a[0].getBrain().isActive(Activity.WORK), "WORK at day time " + h.getLevel().getDayTime());
		});
		seq = seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.LOAD);
			v.snapTo(h.absoluteVec(Vec3.atBottomCenterOf(BuildingTestSupport.inside(ORIGIN_A, 3, 0, 1))));
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, net.minecraft.core.GlobalPos.of(level.dimension(), h.absolutePos(comp)));
			v.setPersistenceRequired();
			level.addFreshEntity(v);
			holder[0] = v;
			track(v);
			h.assertTrue(claims(h, v, comp), "the villager arrives claiming the reserved composter");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the claim is erased", () -> h.assertFalse(claims(h, holder[0], comp), "still claims"));
		seq.thenExecute(() -> {
			h.assertTrue(isHeld(h, comp) && !poiHasSpace(h, comp), "still reserved (the ticket was not double-released)");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- going home and breeding (step 8) -------------------------------------------------------

	/** A valid House (5x4) at {@code origin} with {@code beds} beds (foot z=1, head z=2) and a torch. Returns its marker position. */
	private static BlockPos house(GameTestHelper h, BlockPos origin, int beds) {
		BlockPos marker = BuildingTestSupport.room(h, origin, 5, 4);
		h.setBlock(BuildingTestSupport.inside(origin, 2, 0, 3), Blocks.TORCH);
		for (int i = 0; i < beds; i++) bed(h, BuildingTestSupport.inside(origin, i * 2, 0, 1));
		return marker;
	}

	/** T-L2-12: GO_HOME walks the worker to a bed in a House before 12000; at night it sleeps in that bed. */
	@GameTest(environment = "villageheart-gametest:workers_home", structure = YARD, skyAccess = true, maxTicks = 2400)
	public void workerSleepsInHouseBed(GameTestHelper h) {
		begin(h);
		BlockPos farmMarker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos houseMarker = BuildingTestSupport.markerPos(ORIGIN_B, 5);
		BlockPos comp = BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0);
		BlockPos bedFoot = BuildingTestSupport.inside(ORIGIN_B, 0, 0, 1);
		BlockPos bedHead = bedFoot.south();
		Villager[] v = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			house(h, ORIGIN_B, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, farmMarker, Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, houseMarker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, farmMarker, MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, houseMarker, MarkerStatus.VALID);
			h.assertTrue(isHeld(h, comp) && poiPresent(h, bedHead), "composter reserved, bed POI exists");
		});
		seq = seq.thenExecute(() -> {
			h.getLevel().setDayTime(2000);
			v[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, v[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "reaching the composter", () -> h.assertTrue(distance(h, v[0], comp) <= REACH, "at the job block"));
		seq = seq.thenExecute(() -> h.getLevel().setDayTime(11000));
		seq = seq.thenWaitUntil(() -> h.assertTrue(distance(h, v[0], bedHead) <= 2.0, "distance to the bed " + distance(h, v[0], bedHead)));
		seq = seq.thenExecute(() -> {
			long day = h.getLevel().getDayTime();
			h.assertTrue(day < 12000, "home before sleeping time: day time " + day);
			h.assertFalse(v[0].isSleeping(), "not asleep yet: go_home must not include sleeping");
			h.assertTrue(v[0].getBrain().isActive(WorkerBrain.GO_HOME), "go_home activity");
			h.getLevel().setDayTime(13000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "asleep after 13000", () -> h.assertTrue(v[0].isSleeping(), "sleeping"));
		seq.thenExecute(() -> {
			h.assertValueEqual(java.util.Optional.of(h.absolutePos(bedHead)), v[0].getSleepingPos(), "sleeping in the House's bed");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/**
	 * T-L2-14 (rewritten for M23): hired workers can still breed once the village licenses them: the worker IDLE package keeps
	 * VillagerMakeLove, in the wake-up hours (0 to 1000). The pair is licensed by the test hook (a House bed reserved, as the dawn would),
	 * nobody seeds BREED_TARGET by hand, and there is ONE window: the abort the old three-window retry hid is fixed (plan 3.5) and measured
	 * by the breedingAbortProbe.
	 */
	@GameTest(environment = "villageheart-gametest:workers_breed", structure = YARD, skyAccess = true, maxTicks = 1800)
	public void workersCanBreed(GameTestHelper h) {
		begin(h);
		BlockPos farmMarker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos houseMarker = BuildingTestSupport.markerPos(ORIGIN_B, 5);
		BlockPos bed3 = BuildingTestSupport.inside(ORIGIN_B, 4, 0, 2);
		BlockPos spot = BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1);
		Villager[] v = new Villager[2];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 2);
			house(h, ORIGIN_B, 3); // free beds for the licence to reserve
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, farmMarker, Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, houseMarker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, farmMarker, MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, houseMarker, MarkerStatus.VALID);
			h.assertTrue(poiPresent(h, bed3), "the bed POIs exist");
		});
		seq = seq.thenExecute(() -> {
			// the start of TODAY, not of day 0: a jump back over a day boundary is a dawn (the wage system fires it) and ends the licence
			h.getLevel().setDayTime(dev.villageheart.core.time.DailyClock.dayIndex(h.getLevel().getDayTime()) * 24000L + 10);
			for (int i = 0; i < 2; i++) {
				v[i] = spawnVillager(h, spot);
				v[i].getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4));
				v[i].setAge(0);
				h.assertTrue(hire(h, v[i], JobType.FARMER) instanceof HireResult.Hired, "worker " + i + " hired");
			}
			GrowthTestSupport.license(h, v[0], v[1]);
		});
		seq = seq.thenWaitUntil(() -> {
			StringBuilder d = new StringBuilder(GrowthTestSupport.describe(h));
			for (Villager x : v) d.append(" [").append(x.getBrain().getActiveActivities()).append(" bt=").append(x.getBrain().getMemory(MemoryModuleType.BREED_TARGET).isPresent())
					.append(" can=").append(x.canBreed()).append(" age=").append(x.getAge()).append(" sleeping=").append(x.isSleeping()).append("]");
			h.assertTrue(GrowthTestSupport.babies(h).size() == 1, "no baby yet" + d);
		});
		seq.thenExecute(() -> {
			h.assertTrue(Math.floorMod(h.getLevel().getDayTime(), 24000L) < 1000, "the baby came inside the wake-up window: day time " + h.getLevel().getDayTime());
			h.assertTrue(GrowthTestSupport.licence(h).orElseThrow().state() == dev.villageheart.core.growth.LicenceState.BORN, "the licence is BORN");
			GrowthTestSupport.end(h);
			end(h);
		});
	}

	// ---- deposit trips (step 9, needs M6) --------------------------------------------------------

	/** Farmhouse at A (1 composter), Storage at B (2 chests, optionally full of dirt), both valid; a worker hired inside A at {@code dayTime}. */
	private static GameTestSequence depositSetup(GameTestHelper h, boolean storageFull, long dayTime, Villager[] worker) {
		BlockPos farmMarker = BuildingTestSupport.markerPos(ORIGIN_A, 5);
		BlockPos storeMarker = BuildingTestSupport.markerPos(ORIGIN_B, 5);
		BlockPos chest1 = BuildingTestSupport.inside(ORIGIN_B, 0, 0, 3), chest2 = BuildingTestSupport.inside(ORIGIN_B, 4, 0, 3);
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			farmhouse(h, ORIGIN_A, 1);
			BuildingTestSupport.room(h, ORIGIN_B, 5, 4);
			h.setBlock(chest1, Blocks.CHEST);
			h.setBlock(chest2, Blocks.CHEST);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, farmMarker, Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, storeMarker, Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, farmMarker, MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, storeMarker, MarkerStatus.VALID);
			h.assertTrue(isHeld(h, BuildingTestSupport.inside(ORIGIN_A, 0, 0, 0)), "composter reserved");
		});
		return seq.thenExecute(() -> {
			if (storageFull) {
				StockpileTestSupport.fillAll(h, chest1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 64));
				StockpileTestSupport.fillAll(h, chest2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 64));
			}
			h.getLevel().setDayTime(dayTime);
			worker[0] = spawnVillager(h, BuildingTestSupport.inside(ORIGIN_A, 2, 0, 1));
			h.assertTrue(hire(h, worker[0], JobType.FARMER) instanceof HireResult.Hired, "hired");
		});
	}

	private static long stored(GameTestHelper h, String id) {
		return dev.villageheart.stock.Stockpiles.totalsById(h.getLevel().getServer()).getOrDefault(id, 0L);
	}

	/** T-L2-13: at the end of the day a worker carrying a little hands it in at Storage. */
	@GameTest(environment = "villageheart-gametest:workers_deposit_end", structure = YARD, skyAccess = true, maxTicks = 1600)
	public void depositTripAtEndOfDay(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = depositSetup(h, false, 10900, v).thenExecute(() -> {
			v[0].getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 20));
			h.assertValueEqual(0L, stored(h, "minecraft:wheat"), "Storage starts without wheat");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the 20 wheat reach Storage", () -> {
			h.assertValueEqual(20L, stored(h, "minecraft:wheat"), "wheat in Storage");
			h.assertValueEqual(0, v[0].getInventory().countItem(net.minecraft.world.item.Items.WHEAT), "wheat still carried");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-13b (1): a full load is delivered in the middle of the day. */
	@GameTest(environment = "villageheart-gametest:workers_deposit_full", structure = YARD, skyAccess = true, maxTicks = 1600)
	public void depositTripWhenFull(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = depositSetup(h, false, 3000, v).thenExecute(() -> {
			// A farmer's slot 0 is the hoe. The other seven are the cargo: a full cargo dumps, one stack does not.
			for (int i = 1; i <= 7; i++) v[0].getInventory().setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
			h.assertTrue(h.getLevel().getDayTime() < 11000, "mid-day");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the full cargo reaches Storage", () -> {
			h.assertValueEqual(448L, stored(h, "minecraft:cobblestone"), "cobblestone in Storage");
			h.assertValueEqual(0, v[0].getInventory().countItem(net.minecraft.world.item.Items.COBBLESTONE), "cobblestone still carried");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/**
	 * Review of M11 round 3: a trip flag whose behaviour was dropped without its stop (it never ticks again) must expire. Every job returns early
	 * while a trip "runs", so a flag that stayed set would leave the worker idle at status OK for good.
	 */
	@GameTest(environment = "villageheart-gametest:workers_deposit_stale", structure = YARD, skyAccess = true, maxTicks = 600)
	public void deadDepositTripFlagExpires(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = depositSetup(h, false, 3000, v).thenExecute(() -> {
			Workers.markTripRunningForTest(v[0]);
			h.assertTrue(Workers.tripRunning(v[0]), "the flag is set");
		});
		seq = seq.thenIdle(50).thenExecute(() -> h.assertTrue(Workers.tripRunning(v[0]), "a fresh flag still counts"));
		seq = seq.thenIdle(120);
		seq.thenExecute(() -> {
			h.assertTrue(!Workers.tripRunning(v[0]), "the flag of a trip that no longer ticks has expired");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** T-L2-13b (2): with every Storage container full the worker keeps its load, reports STORAGE_FULL and the village alert shows it. */
	@GameTest(environment = "villageheart-gametest:workers_storage_full", structure = YARD, skyAccess = true, maxTicks = 900)
	public void depositTripStorageFull(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = depositSetup(h, true, 3000, v).thenExecute(() -> {
			for (int i = 1; i <= 7; i++) v[0].getInventory().setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "STORAGE_FULL is reported", () -> {
			var rec = record(h, v[0]).orElseThrow();
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.STORAGE_FULL, Workers.status(h.getLevel().getServer(), rec), "status");
			h.assertValueEqual(dev.villageheart.core.stock.StorageAlert.FULL, dev.villageheart.stock.Stockpiles.alert(h.getLevel().getServer()), "village alert");
		});
		seq = seq.thenIdle(100).thenExecute(() -> {
			h.assertValueEqual(448, v[0].getInventory().countItem(net.minecraft.world.item.Items.COBBLESTONE), "the worker keeps what it carries");
			h.assertValueEqual(0L, stored(h, "minecraft:cobblestone"), "nothing was stored");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- review r1: releasing the Heart with an unloaded workstation --------------------------------

	/**
	 * A held workstation whose chunk is unloaded when the Heart is broken: the ticket cannot be released then, so it must be remembered
	 * in the saved data and released when the chunk loads, after which a vanilla villager can take the composter.
	 */
	@GameTest(environment = "villageheart-gametest:workers_release_unloaded", structure = YARD, maxTicks = 3000)
	public void heartReleaseWithUnloadedWorkstation(GameTestHelper h) {
		begin(h);
		BlockPos[] far = new BlockPos[1];
		HeartLocation[] farLoc = new HeartLocation[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			MinecraftServer server = level.getServer();
			HeartLocation heart = VillageClaims.current(server).orElseThrow().heart();
			int fx = heart.x(), fz = heart.z() + 100_000;
			level.getChunk(fx >> 4, fz >> 4);
			level.setChunkForced(fx >> 4, fz >> 4, true);
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, fx, fz);
			far[0] = new BlockPos(fx, y, fz);
			farLoc[0] = VillageClaims.locate(level, far[0]);
			level.setBlock(far[0], Blocks.COMPOSTER.defaultBlockState(), 3);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getPoiManager().getType(far[0]).isPresent(), "the far composter's POI exists"));
		seq = seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			h.assertTrue(level.getPoiManager().take(t -> true, (t, p) -> p.equals(far[0]), far[0], 1).isPresent(), "the mod took the ticket");
			VillageClaims.data(level.getServer()).setReservedJobBlocks(Set.of(farLoc[0]));
			// the reservation pass must keep it held while the chunk is still loaded
			Workers.setJobSiteSourceForTest(new JobSiteSource() {
				@Override public List<SiteView> sites(MinecraftServer s, JobType job) { return List.of(); }
				@Override public boolean isRegistered(MinecraftServer s, HeartLocation b) { return true; }
				@Override public List<HeartLocation> reservable(MinecraftServer s) { return List.of(farLoc[0]); }
				@Override public boolean contentsPending(MinecraftServer s) { return true; }
			});
			level.setChunkForced(far[0].getX() >> 4, far[0].getZ() >> 4, false);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far[0].getX() >> 4, far[0].getZ() >> 4) == null,
				"waiting for the far chunk to unload"));
		seq = seq.thenExecute(() -> {
			BuildingTestSupport.owner().gameMode.destroyBlock(h.absolutePos(new BlockPos(3, 1, 1)));
			MinecraftServer server = h.getLevel().getServer();
			h.assertTrue(VillageClaims.current(server).isEmpty(), "the village is released");
			h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far[0].getX() >> 4, far[0].getZ() >> 4) == null, "the chunk is still unloaded");
			h.assertTrue(VillageClaims.data(server).pendingTicketReleases().contains(farLoc[0]),
					"the unloaded release is remembered: " + VillageClaims.data(server).pendingTicketReleases());
			h.getLevel().setChunkForced(far[0].getX() >> 4, far[0].getZ() >> 4, true);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 80, "the pending release completes after the chunk loads", () -> {
			MinecraftServer server = h.getLevel().getServer();
			h.assertFalse(VillageClaims.data(server).pendingTicketReleases().contains(farLoc[0]), "still pending");
			h.assertTrue(poiHasSpaceAbs(h, far[0]), "the composter's ticket is free");
		});
		// vanilla can take it again: the very query a villager's AcquirePoi makes (acquirable job site, free ticket) now finds it
		seq = seq.thenExecute(() -> {
			var poi = h.getLevel().getPoiManager();
			h.assertTrue(poi.take(VillagerProfession.ALL_ACQUIRABLE_JOBS, (t, p) -> p.equals(far[0]), far[0], 1).isPresent(),
					"a villager's job-site search takes the freed composter");
		});
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			level.setBlock(far[0], Blocks.AIR.defaultBlockState(), 3);
			level.setChunkForced(far[0].getX() >> 4, far[0].getZ() >> 4, false);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/**
	 * The reach dead band: {@code WalkTo} used to report ARRIVED up to 0.1 block beyond the reach it was given, while every job works only
	 * within the reach itself, so a worker 0.05 beyond it stood frozen for the whole day (the "progress after the evening: 4003.0" flake).
	 * Every job walks through {@code WalkTo}, so this covers all callers: for the reaches they use (chest 2.0, job block 1.5, walk-in 3.0;
	 * item pickups ask 0.8 but take by their own COLLECT_TAKE distance and ignore ARRIVED) a villager placed reach + 0.05 from the target is not "arrived" and walks until it is within the reach.
	 */
	@GameTest(environment = "villageheart-gametest:workers_walkto_band", structure = YARD, skyAccess = true, maxTicks = 600)
	public void walkToNeverArrivesBeyondItsReach(GameTestHelper h) {
		begin(h);
		double[] reaches = {1.5, 2.0, 3.0};
		Villager v = spawnVillager(h, new BlockPos(10, 0, 10));
		dev.villageheart.worker.job.WalkTo walk = new dev.villageheart.worker.job.WalkTo();
		BlockPos target = h.absolutePos(new BlockPos(16, 0, 16));
		GameTestSequence seq = h.startSequence().thenIdle(2);
		for (double reach : reaches) {
			seq = seq.thenExecute(() -> {
				v.setNoAi(false);
				v.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
				net.minecraft.world.phys.Vec3 c = net.minecraft.world.phys.Vec3.atBottomCenterOf(target);
				v.setPos(c.x - (reach + 0.05), c.y, c.z);
				v.getNavigation().stop();
				walk.release(v);
				walk.begin(target, reach, h.getLevel().getGameTime());
				double d = dev.villageheart.worker.job.WalkTo.distance(v, target);
				h.assertTrue(d > reach, "placed beyond the reach " + reach + ": " + d);
				var st = walk.tick(v, h.getLevel().getGameTime());
				h.assertTrue(st != dev.villageheart.worker.job.WalkTo.State.ARRIVED, "not arrived at " + d + " with reach " + reach + ": " + st);
			});
			seq = seq.thenWaitUntil(() -> {
				var st = walk.tick(v, h.getLevel().getGameTime());
				double d = dev.villageheart.worker.job.WalkTo.distance(v, target);
				h.assertTrue(st == dev.villageheart.worker.job.WalkTo.State.ARRIVED && d <= reach, "walking to reach " + reach + ": " + st + " at " + d);
			});
		}
		seq.thenExecute(() -> {
			v.discard();
			VillageTestSupport.done(h);
			end(h);
		});
	}

	private static boolean poiHasSpaceAbs(GameTestHelper h, BlockPos abs) {
		return h.getLevel().getPoiManager().getInSquare(t -> true, abs, 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				.anyMatch(r -> r.getPos().equals(abs));
	}
}
