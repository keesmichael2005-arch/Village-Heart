package dev.villageheart.gametest;

import static dev.villageheart.gametest.GuardTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.guard.Shift;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.guard.GuardSystem;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Layer 2: guards (M18). Rules: docs/TESTING.md "Guard tests (M18+)": own environment per test ({@code guards_<name>}, natural spawning
 * off), structure {@code yard} with sky access, scene first, {@code VillageTestSupport.done} + {@code WorkerTestSupport.end} last.
 */
public class GuardGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	// ---- T-L2-2 ------------------------------------------------------------------------------------

	/** A day guard is on duty at noon, not asleep; the night guard of the same Barracks is asleep in its bed. */
	@GameTest(environment = "villageheart-gametest:guards_day_guard_noon", structure = YARD, skyAccess = true, maxTicks = 1600)
	public void dayGuardOnDutyAtNoon(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[2];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			setTime(h, 5800);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			g[1] = hireGuard(h, spawnSpot().east(), Optional.of(Shift.NIGHT));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the day guard is on duty and the night guard asleep", () -> {
			h.assertTrue(g[0].getBrain().isActive(GuardActivities.DUTY), "the day guard is on GUARD_DUTY at " + h.getLevel().getDayTime());
			h.assertTrue(!g[0].isSleeping(), "the day guard is awake");
			h.assertTrue(g[1].isSleeping(), "the night guard sleeps (activity rest: " + g[1].getBrain().isActive(Activity.REST) + ")");
		});
		seq.thenExecute(() -> {
			h.assertTrue(g[1].getBrain().isActive(Activity.REST), "the night guard's activity is REST");
			System.out.println("[m18] dayGuardOnDutyAtNoon: settled " + (h.getTick() - t0[0]) + " ticks after hire");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ------------------------------------------------------------------------------------

	/** The Barracks bed is held by the mod: an unemployed villager sleeps in the House bed, never in the guard's bed. */
	@GameTest(environment = "villageheart-gametest:guards_bed_reserved", structure = YARD, skyAccess = true, maxTicks = 3600)
	public void barracksBedReserved(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			NeedsTestSupport.buildHouse(h, 1);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, NeedsTestSupport.houseMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertTrue(WorkerTestSupport.poiPresent(h, NeedsTestSupport.houseBedHead(0)), "the House bed has a POI");
			h.assertTrue(freeTickets(h, bedHead(0)) == 0, "the mod holds the Barracks bed's ticket");
		}).thenExecute(() -> {
			setTime(h, 12500);
			v[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(BARRACKS, 3, 0, 1));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2400, "the unemployed villager takes the House bed", () ->
				h.assertTrue(NeedsTestSupport.homeIs(h, v[0], NeedsTestSupport.houseBedHead(0)), "HOME is the House bed, got "
						+ v[0].getBrain().getMemory(MemoryModuleType.HOME)));
		seq.thenIdle(300).thenExecute(() -> {
			h.assertTrue(freeTickets(h, bedHead(0)) == 0, "the Barracks bed still has no free ticket");
			h.assertTrue(!v[0].getSleepingPos().filter(p -> p.equals(h.absolutePos(bedHead(0)))).isPresent(), "never slept in the Barracks bed");
			System.out.println("[m18] barracksBedReserved: House bed taken " + (h.getTick() - t0[0]) + " ticks after the spawn");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Control for {@link #barracksBedReserved}: without the Barracks (marker removed) the same bed is free and the villager takes it. */
	@GameTest(environment = "villageheart-gametest:guards_bed_control", structure = YARD, skyAccess = true, maxTicks = 3600)
	public void barracksBedControlFreeWithoutMarker(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenWaitUntil(() -> h.assertTrue(freeTickets(h, bedHead(0)) == 0, "held while the Barracks stands"))
				.thenExecute(() -> {
					h.setBlock(barracksMarker(), net.minecraft.world.level.block.Blocks.AIR);
					setTime(h, 12500);
					v[0] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(BARRACKS, 3, 0, 1));
				});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2400, "the villager takes the freed Barracks bed", () ->
				h.assertTrue(NeedsTestSupport.homeIs(h, v[0], bedHead(0)), "HOME is the old Barracks bed, got " + v[0].getBrain().getMemory(MemoryModuleType.HOME)));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-4 (roadmap) --------------------------------------------------------------------------

	/**
	 * A night guard sleeps by day (asleep in its Barracks bed at 6000) and patrols at night (on duty at 18000, reaches a patrol point other than the
	 * one nearest its bed, and never dwells on a door block or the cell right outside a door).
	 */
	@GameTest(environment = "villageheart-gametest:guards_night_patrol", structure = YARD, skyAccess = true, maxTicks = 3600)
	public void nightGuardSleepsByDayPatrolsAtNight(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		boolean[] onDoor = new boolean[1];
		java.util.Set<BlockPos> reached = new java.util.HashSet<>();
		long[] t0 = new long[1];
		BlockPos doorRel = new BlockPos(BuildingTestSupport.markerPos(BARRACKS, 5).getX(), 1, BARRACKS.getZ());
		h.onEachTick(() -> {
			if (g[0] == null) return;
			dev.villageheart.worker.guard.GuardRuntime.lastArrival(g[0].getUUID()).ifPresent(reached::add);
			if (dev.villageheart.worker.guard.GuardRuntime.isDwelling(g[0].getUUID(), h.getLevel().getGameTime())) {
				BlockPos rel = h.relativePos(g[0].blockPosition());
				// compared in world space: the door and the cells in front of it
				BlockPos door = h.absolutePos(doorRel);
				BlockPos p = g[0].blockPosition();
				if (p.getX() == door.getX() && p.getZ() == door.getZ() || p.getX() == door.getX() && Math.abs(p.getZ() - door.getZ()) <= 1
						|| p.getZ() == door.getZ() && Math.abs(p.getX() - door.getX()) <= 1) onDoor[0] = true;
			}
		});
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			BuildingTestSupport.room(h, STORE, 5, 4);
			h.setBlock(BuildingTestSupport.inside(STORE, 0, 0, 3), net.minecraft.world.level.block.Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(STORE, 4, 0, 3), net.minecraft.world.level.block.Blocks.CHEST);
			BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, storeMarker(), net.minecraft.core.Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID)).thenExecute(() -> {
			setTime(h, 4000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.NIGHT));
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(dev.villageheart.worker.guard.GuardSystem.bedOf(g[0].getUUID()).filter(b -> b.equals(h.absolutePos(bedHead(0)))).isPresent(), "the guard's bed is assigned");
			h.assertTrue(!h.getBlockState(bedHead(0)).getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED), "the bed is free before");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 6000, "waiting for 6000"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(g[0].isSleeping(), "asleep at day time " + h.getLevel().getDayTime());
			h.assertTrue(g[0].getSleepingPos().filter(p -> p.equals(h.absolutePos(bedHead(0)))).isPresent(), "in its own bed: " + g[0].getSleepingPos());
			h.assertTrue(g[0].getBrain().isActive(Activity.REST), "activity REST");
			setTime(h, 17000);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 18000, "waiting for 18000"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(!g[0].isSleeping(), "awake at 18000");
			h.assertTrue(g[0].getBrain().isActive(GuardActivities.DUTY), "activity GUARD_DUTY");
			h.assertValueEqual(dev.villageheart.worker.guard.GuardRuntime.Mode.PATROL, dev.villageheart.worker.guard.GuardRuntime.modeOf(g[0].getUUID()), "mode");
			reached.clear();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the guard reaches a patrol point other than the one near its bed", () -> {
			var points = dev.villageheart.worker.guard.PatrolPoints.of(h.getLevel().getServer());
			BlockPos bed = h.absolutePos(bedHead(0));
			BlockPos nearestToBed = points.stream().min(java.util.Comparator.comparingDouble(p -> p.distSqr(bed))).orElseThrow();
			h.assertTrue(reached.stream().anyMatch(p -> !p.equals(nearestToBed)), "reached " + reached + " of " + points + "; nearest to the bed " + nearestToBed);
		});
		seq.thenExecute(() -> {
			h.assertTrue(!onDoor[0], "the guard never dwelt on a door block or next to it");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-17 (BACKLOG M21, blocking) -----------------------------------------------------------

	/** The finished Lamp Posts of M21 are patrol points: the guard walks to one, and a removed lantern takes the point away. */
	@GameTest(environment = "villageheart-gametest:guards_lamp_post", structure = YARD, skyAccess = true, maxTicks = 3200)
	public void guardPatrolsLampPost(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		BlockPos lamp = new BlockPos(26, -1, 18);
		BlockPos[] point = new BlockPos[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			placeLampPost(h, lamp);
			setTime(h, 2000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			var points = dev.villageheart.defence.Defences.patrolPoints(h.getLevel().getServer());
			h.assertTrue(points.size() == 1, "one defence patrol point: " + points);
			point[0] = points.get(0);
			h.assertTrue(dev.villageheart.worker.guard.PatrolPoints.of(h.getLevel().getServer()).contains(point[0]), "PatrolPoints.of contains the Lamp Post's point");
			h.assertTrue(point[0].distSqr(h.absolutePos(lamp)) <= 4, "next to the post: " + point[0]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the guard walks to the Lamp Post", () ->
				h.assertTrue(g[0].position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(point[0])) <= 1.5, "guard at " + g[0].blockPosition() + ", point " + point[0]));
		seq = seq.thenExecute(() -> h.setBlock(lamp.above(3), net.minecraft.world.level.block.Blocks.AIR));
		seq = BuildingTestSupport.awaitWithin(h, seq, dev.villageheart.core.Balance.DEFENCE_REFRESH_TICKS + 260, "the removed lantern takes the point away", () ->
				h.assertTrue(!dev.villageheart.worker.guard.PatrolPoints.of(h.getLevel().getServer()).contains(point[0]), "the point is still there"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A Lamp Post as M21 builds one (oak fence, oak fence, lantern) on the ground block {@code groundRel}, recorded as a defence site. */
	static void placeLampPost(GameTestHelper h, BlockPos groundRel) {
		h.setBlock(groundRel.above(), net.minecraft.world.level.block.Blocks.OAK_FENCE);
		h.setBlock(groundRel.above(2), net.minecraft.world.level.block.Blocks.OAK_FENCE);
		h.setBlock(groundRel.above(3), net.minecraft.world.level.block.Blocks.LANTERN);
		var loc = WorkerTestSupport.loc(h, groundRel);
		var site = new dev.villageheart.core.defence.DefenceSite(java.util.UUID.randomUUID(), "lamp_post", loc, dev.villageheart.core.blueprint.Rotation4.NONE, 1L);
		dev.villageheart.world.VillageClaims.data(h.getLevel().getServer()).update(v -> v.withDefence(site));
	}

	static long freeTickets(GameTestHelper h, BlockPos headRel) {
		BlockPos head = h.absolutePos(headRel);
		var poi = h.getLevel().getPoiManager();
		return poi.getInSquare(t -> true, head, 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
				.filter(r -> r.getPos().equals(head)).count();
	}
}
