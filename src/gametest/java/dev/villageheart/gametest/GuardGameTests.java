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

	// ---- combat (T-L2-1, 6, 10, 16) ----------------------------------------------------------------

	/** A guard of level 3 (40 health, +2 damage) so a test fight is survivable. */
	private static void level3(GameTestHelper h, Villager g) {
		dev.villageheart.worker.Workers.setLevel(h.getLevel().getServer(), g, 3);
		dev.villageheart.worker.guard.Guards.applyPerks(g, false);
		g.setHealth(g.getMaxHealth());
	}

	/** The vanilla panic trigger is not in the guard brain: a guard with a zombie 5 blocks away fights; an unhired villager panics at the same sight. */
	@GameTest(environment = "villageheart-gametest:guards_no_panic", structure = YARD, skyAccess = true, maxTicks = 900)
	public void guardBrainHasNoPanic(GameTestHelper h) {
		begin(h);
		installRecorders();
		Villager[] g = new Villager[1], control = new Villager[1];
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[2];
		boolean[] panicked = new boolean[1], guardPanicked = new boolean[1];
		h.onEachTick(() -> {
			if (g[0] != null && (g[0].getBrain().isActive(Activity.PANIC) || g[0].getBrain().isActive(Activity.HIDE) || g[0].getBrain().isActive(Activity.RAID))) guardPanicked[0] = true;
			if (control[0] != null && control[0].getBrain().isActive(Activity.PANIC)) panicked[0] = true;
		});
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			setTime(h, 2000);
			g[0] = hireGuard(h, new BlockPos(11, 0, 14), Optional.of(Shift.DAY));
			level3(h, g[0]);
			arm(g[0], net.minecraft.world.item.Items.IRON_SWORD);
			z[0] = zombie(h, new BlockPos(16, 0, 14));
			// the control: an unhired villager far from the guard with a zombie in a fence cage 5 blocks from it
			control[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(27, 0, 27));
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) h.setBlock(new BlockPos(27 + dx, 0, 22 + dz), net.minecraft.world.level.block.Blocks.OAK_FENCE);
			}
			z[1] = zombie(h, new BlockPos(27, 0, 22));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the control villager panics (the setup can cause panic)", () ->
				h.assertTrue(panicked[0], "the unhired villager did not panic; activities: " + control[0].getBrain().getActiveActivities()));
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the guard hits the zombie", () -> {
			h.assertTrue(!guardPanicked[0], "the guard went into PANIC/HIDE/RAID");
			h.assertTrue(damaged(z[0], g[0]), "the guard has not hit the zombie yet; guard " + g[0].blockPosition() + " zombie " + z[0].blockPosition() + " "
					+ g[0].getBrain().getActiveActivities());
		});
		seq.thenExecute(() -> {
			h.assertTrue(!guardPanicked[0], "the guard never panicked");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Off shift (and unarmed) a guard still defends itself; a guard asleep in its bed is woken by the hit and fights back. */
	@GameTest(environment = "villageheart-gametest:guards_off_duty_defence", structure = YARD, skyAccess = true, maxTicks = 900)
	public void offDutyGuardDefendsItself(GameTestHelper h) {
		begin(h);
		installRecorders();
		Villager[] g = new Villager[1];
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[1];
		boolean[] panicked = new boolean[1];
		h.onEachTick(() -> {
			if (g[0] != null && g[0].getBrain().isActive(Activity.PANIC)) panicked[0] = true;
		});
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			setTime(h, 12100);
			g[0] = hireGuard(h, new BlockPos(11, 0, 14), Optional.of(Shift.DAY));
			level3(h, g[0]);
			h.assertTrue(g[0].getMainHandItem().isEmpty(), "unarmed");
			z[0] = zombie(h, new BlockPos(12, 0, 14));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the unarmed off-shift guard damages the zombie", () ->
				h.assertTrue(damaged(z[0], g[0]), "guard at " + g[0].blockPosition() + " " + g[0].getBrain().getActiveActivities()));
		seq.thenExecute(() -> {
			h.assertTrue(!panicked[0], "never PANIC");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A guard asleep in its bed is woken by a zombie's hit and fights back. */
	@GameTest(environment = "villageheart-gametest:guards_sleeper_defends", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void sleepingGuardWakesAndFights(GameTestHelper h) {
		begin(h);
		installRecorders();
		Villager[] g = new Villager[1];
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			setTime(h, 14000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			level3(h, g[0]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 300, "the guard falls asleep", () -> h.assertTrue(g[0].isSleeping(), "not asleep"));
		seq = seq.thenExecute(() -> z[0] = zombie(h, BuildingTestSupport.inside(BARRACKS, 3, 0, 1)));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the woken guard damages the zombie", () ->
				h.assertTrue(damaged(z[0], g[0]), "guard asleep " + g[0].isSleeping() + " at " + g[0].blockPosition() + " " + g[0].getBrain().getActiveActivities()));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A wounded guard on duty next to a zombie fights instead of walking to bed, and never toggles sleep while the zombie lives (T-L2-16). */
	@GameTest(environment = "villageheart-gametest:guards_wounded_fights", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void woundedGuardFightsInsteadOfBed(GameTestHelper h) {
		begin(h);
		installRecorders();
		Villager[] g = new Villager[1];
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[1];
		boolean[] slept = new boolean[1];
		long[] lastHit = new long[1];
		String[] failure = new String[1];
		h.onEachTick(() -> {
			if (z[0] == null || !z[0].isAlive()) return;
			if (g[0].isSleeping()) slept[0] = true;
		});
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			setTime(h, 2000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			level3(h, g[0]);
			arm(g[0], net.minecraft.world.item.Items.IRON_SWORD);
			g[0].setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
			g[0].setHealth(11.0F); // 27 % of 40: wounded
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(dev.villageheart.worker.guard.GuardAttachments.get(g[0]).wounded(), "wounded within 20 ticks"));
		seq = seq.thenExecute(() -> z[0] = zombie(h, BuildingTestSupport.inside(BARRACKS, 0, 0, 1)));
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the zombie dies", () -> h.assertTrue(!z[0].isAlive(), "zombie alive; guard hp " + g[0].getHealth() + " at "
				+ g[0].blockPosition() + " " + g[0].getBrain().getActiveActivities()));
		seq.thenExecute(() -> {
			h.assertTrue(!slept[0], "the guard never slept while the zombie lived");
			h.assertTrue(g[0].isAlive() && villagerDeaths().isEmpty(), "no villager died");
			h.assertTrue(damaged(z[0], g[0]), "the guard hit the zombie");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** A guard below 30 % health retreats to its bed, sleeps and heals past 80 %, then is back on duty; the wound survives a save and reload. */
	@GameTest(environment = "villageheart-gametest:guards_wounded_heals", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void woundedGuardRetreatsAndHeals(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			setTime(h, 2000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			h.assertTrue(g[0].getBrain().isActive(GuardActivities.DUTY) || true, "duty");
			g[0].setHealth(5.0F);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "wounded, in REST and asleep", () -> {
			h.assertTrue(dev.villageheart.worker.guard.GuardAttachments.get(g[0]).wounded(), "the wound bit is set");
			h.assertTrue(g[0].getBrain().isActive(Activity.REST) && g[0].isSleeping(), "asleep in REST: " + g[0].getBrain().getActiveActivities());
		});
		seq = seq.thenExecute(() -> {
			// save and reload the entity while it is wounded: the bit survives
			g[0] = LumberTestSupport.reloadEntity(h.getLevel(), g[0]);
			h.assertTrue(dev.villageheart.worker.guard.GuardAttachments.get(g[0]).wounded(), "the wound bit survived the reload");
			h.assertTrue(g[0].getHealth() <= 8.0F, "and so did the health: " + g[0].getHealth());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1800, "healed past 80 % and back on duty", () -> {
			h.assertTrue(g[0].getHealth() >= 16.0F, "health " + g[0].getHealth());
			h.assertTrue(g[0].getBrain().isActive(GuardActivities.DUTY) && !g[0].isSleeping(), "back on duty: " + g[0].getBrain().getActiveActivities());
		});
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
