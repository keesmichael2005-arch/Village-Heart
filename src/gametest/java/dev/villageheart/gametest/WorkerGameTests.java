package dev.villageheart.gametest;

import static dev.villageheart.gametest.WorkerTestSupport.*;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.SiteView;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.JobSiteSource;
import dev.villageheart.worker.Workers;
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

	// ---- reservations (step 5) -----------------------------------------------------------------

	private static void breakMarker(GameTestHelper h, BlockPos marker) {
		ServerPlayer breaker = VillageTestSupport.player(h, "breaker", GameType.SURVIVAL);
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
