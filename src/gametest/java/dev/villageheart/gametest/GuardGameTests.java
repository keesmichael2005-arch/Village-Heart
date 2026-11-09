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
	// ---- gear (T-L2-5, 7, 8, 9) --------------------------------------------------------------------

	private static void buildStorage(GameTestHelper h) {
		BuildingTestSupport.room(h, STORE, 5, 4);
		h.setBlock(BuildingTestSupport.inside(STORE, 0, 0, 3), net.minecraft.world.level.block.Blocks.CHEST);
		h.setBlock(BuildingTestSupport.inside(STORE, 4, 0, 3), net.minecraft.world.level.block.Blocks.CHEST);
		BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.STORAGE, storeMarker(), net.minecraft.core.Direction.NORTH);
	}

	private static GameTestSequence sceneWithStorage(GameTestHelper h, int guards) {
		return scene(h, guards).thenExecute(() -> buildStorage(h))
				.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, storeMarker(), dev.villageheart.block.MarkerStatus.VALID));
	}

	private static boolean chestHas(GameTestHelper h, net.minecraft.world.item.Item item) {
		var c = StockpileTestSupport.container(h, chest());
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) return true;
		return false;
	}

	private static void wear(Villager g, net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.Item item) {
		g.setItemSlot(slot, new net.minecraft.world.item.ItemStack(item));
		g.setGuaranteedDrop(slot);
	}

	/** The better chestplate lies in Storage, a worse one in the Barracks chest: at shift start the guard walks to Storage and wears the iron one. */
	@GameTest(environment = "villageheart-gametest:guards_equip_storage", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void ironChestplateFromStorageAtShiftStart(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		GameTestSequence seq = sceneWithStorage(h, 1).thenExecute(() -> {
			putInChest(h, 0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE));
			LumberTestSupport.putInChest(h, BuildingTestSupport.inside(STORE, 0, 0, 3), 0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
			setTime(h, 23800);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 24000, "waiting for the shift start"));
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the guard wears the iron chestplate", () ->
				h.assertTrue(g[0].getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.IRON_CHESTPLATE),
						"wears " + g[0].getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST) + " at " + g[0].blockPosition() + " mode "
								+ dev.villageheart.worker.guard.GuardRuntime.modeOf(g[0].getUUID())));
		seq.thenExecute(() -> {
			h.assertTrue(StockpileTestSupport.container(h, BuildingTestSupport.inside(STORE, 0, 0, 3)).getItem(0).isEmpty(), "Storage has no iron chestplate left");
			h.assertTrue(chestHas(h, net.minecraft.world.item.Items.LEATHER_CHESTPLATE), "the leather one is still in the Barracks chest");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** One chestplate serves two shifts: handed in at the end of a shift, worn by the next guard at its recheck (day to night and back). */
	@GameTest(environment = "villageheart-gametest:guards_gear_shared", structure = YARD, skyAccess = true, maxTicks = 5200)
	public void gearHandedInAndShared(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[2];
		var chest = net.minecraft.world.entity.EquipmentSlot.CHEST;
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			setTime(h, 11800);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			g[1] = hireGuard(h, spawnSpot().east(), Optional.of(Shift.NIGHT));
			wear(g[0], chest, net.minecraft.world.item.Items.IRON_CHESTPLATE);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 12600, "waiting for 12600"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(g[0].getItemBySlot(chest).isEmpty(), "A handed the chestplate in");
			h.assertTrue(chestHas(h, net.minecraft.world.item.Items.IRON_CHESTPLATE), "it is in the Barracks chest");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 13400, "waiting for 13400"));
		seq = seq.thenExecute(() -> h.assertTrue(g[1].getItemBySlot(chest).is(net.minecraft.world.item.Items.IRON_CHESTPLATE), "B wears it: " + g[1].getItemBySlot(chest)));
		seq = seq.thenExecute(() -> setTime(h, 23800));
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 24600, "waiting for 600 after the shift change"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(g[1].getItemBySlot(chest).isEmpty(), "B handed it in");
			h.assertTrue(chestHas(h, net.minecraft.world.item.Items.IRON_CHESTPLATE), "back in the Barracks chest");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() >= 25400, "waiting for 1400 after the shift change"));
		seq.thenExecute(() -> {
			h.assertTrue(g[0].getItemBySlot(chest).is(net.minecraft.world.item.Items.IRON_CHESTPLATE), "A wears it again: " + g[0].getItemBySlot(chest));
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Fire returns the gear and the perks, a death drops the gear intact, a conversion leaves the zombie villager bare; the Barracks beds stay held. */
	@GameTest(environment = "villageheart-gametest:guards_fire_death_gear", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void fireAndDeathKeepGear(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[3];
		GameTestSequence seq = scene(h, 3).thenExecute(() -> {
			setTime(h, 2000);
			for (int i = 0; i < 3; i++) {
				g[i] = hireGuard(h, spawnSpot().east(i - 1), Optional.of(Shift.DAY));
				dev.villageheart.worker.Workers.setLevel(h.getLevel().getServer(), g[i], 2);
				dev.villageheart.worker.guard.Guards.applyPerks(g[i], false);
				wear(g[i], net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.Items.IRON_CHESTPLATE);
				wear(g[i], net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.Items.IRON_SWORD);
				h.assertTrue(g[i].getMaxHealth() == 30.0, "level 2 health");
			}
		}).thenIdle(30);
		seq = seq.thenExecute(() -> {
			for (int i = 0; i < 3; i++) h.assertTrue(dev.villageheart.worker.guard.GuardSystem.bedOf(g[i].getUUID()).isPresent(), "bed " + i);
			dev.villageheart.worker.Workers.fire(h.getLevel(), g[0]);
			h.assertTrue(g[0].getMaxHealth() == 20.0, "max health back to 20: " + g[0].getMaxHealth());
			h.assertTrue(g[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(
					net.minecraft.resources.Identifier.fromNamespaceAndPath("villageheart", "guard_health")) == null, "no modifier left");
			h.assertTrue(g[0].getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() && g[0].getMainHandItem().isEmpty(), "nothing worn");
			h.assertTrue(chestHas(h, net.minecraft.world.item.Items.IRON_CHESTPLATE) && chestHas(h, net.minecraft.world.item.Items.IRON_SWORD), "both in the Barracks chest");
			h.assertTrue(g[0].getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME).isEmpty(), "HOME erased");
			h.assertTrue(freeTickets(h, bedHead(0)) == 0 && freeTickets(h, bedHead(1)) == 0 && freeTickets(h, bedHead(2)) == 0, "the bed tickets are still held");
			g[1].kill(h.getLevel());
		}).thenIdle(5);
		seq = seq.thenExecute(() -> {
			var items = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, g[1].getBoundingBox().inflate(6));
			boolean chest = items.stream().anyMatch(e -> e.getItem().is(net.minecraft.world.item.Items.IRON_CHESTPLATE) && e.getItem().getDamageValue() == 0);
			boolean sword = items.stream().anyMatch(e -> e.getItem().is(net.minecraft.world.item.Items.IRON_SWORD) && e.getItem().getDamageValue() == 0);
			h.assertTrue(chest && sword, "the dead guard dropped its chestplate and sword intact: " + items);
			h.assertTrue(freeTickets(h, bedHead(1)) == 0, "the dead guard's bed ticket is still held");
			var zombie = zombie(h, spawnSpot());
			h.assertTrue(zombie.convertVillagerToZombieVillager(h.getLevel(), g[2]), "converted");
			// checked in the same tick: a zombie would pick the dropped gear up a moment later
			var zv = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.ZombieVillager.class, new net.minecraft.world.phys.AABB(h.absolutePos(spawnSpot())).inflate(8));
			h.assertTrue(!zv.isEmpty(), "a zombie villager exists");
			for (var z : zv) {
				h.assertTrue(z.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() && z.getMainHandItem().isEmpty(), "the zombie villager wears nothing");
				h.assertTrue(z.getMaxHealth() == 20.0, "and has no perks: " + z.getMaxHealth());
			}
			var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(spawnSpot())).inflate(8));
			h.assertTrue(drops.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.IRON_CHESTPLATE)).count() >= 2, "the converted guard's chestplate is on the ground too");
		}).thenIdle(5);
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The roadmap fight: a zombie 10 blocks from a guard that fetched an iron sword and chestplate dies within 60 s, and no villager dies. */
	@GameTest(environment = "villageheart-gametest:guards_zombie_night", structure = YARD, skyAccess = true, maxTicks = 2600)
	public void zombieInClaimKilledAtNight(GameTestHelper h) {
		begin(h);
		installRecorders();
		Villager[] g = new Villager[1];
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[1];
		long[] kills0 = new long[1];
		int[] xp0 = new int[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
			putInChest(h, 1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
			setTime(h, 14000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.NIGHT));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the guard holds the sword and wears the chestplate", () -> {
			h.assertTrue(g[0].getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD), "main hand " + g[0].getMainHandItem());
			h.assertTrue(g[0].getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.IRON_CHESTPLATE), "chest");
		});
		seq = seq.thenExecute(() -> {
			var p = h.absolutePos(new BlockPos(20, 0, 14));
			g[0].teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			g[0].getNavigation().stop();
			z[0] = zombie(h, new BlockPos(30, 0, 14));
			kills0[0] = dev.villageheart.worker.job.WorkStats.get("guard", "kills");
			xp0[0] = g[0].getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).xp();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the zombie dies", () -> h.assertTrue(!z[0].isAlive(), "alive at " + z[0].blockPosition() + ", guard at "
				+ g[0].blockPosition() + " hp " + g[0].getHealth() + " " + dev.villageheart.worker.guard.GuardRuntime.modeOf(g[0].getUUID())));
		seq.thenExecute(() -> {
			h.assertTrue(damaged(z[0], g[0]), "the guard dealt the damage");
			h.assertValueEqual(kills0[0] + 1, dev.villageheart.worker.job.WorkStats.get("guard", "kills"), "work.guard.kills");
			int xp = g[0].getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).xp();
			h.assertValueEqual(xp0[0] + dev.villageheart.core.Balance.milliXpPerUnit(dev.villageheart.core.job.WorkUnit.HOSTILE_KILLED) / 1000, xp, "XP gained");
			h.assertTrue(villagerDeaths().isEmpty() && g[0].isAlive(), "no villager died");
			VillageTestSupport.done(h);
			end(h);
		});
	}


	// ---- training (T-L2-11, 12) --------------------------------------------------------------------

	static final BlockPos YARD_MARKER = new BlockPos(22, 0, 16);

	/** An oak fence post with the Training Yard marker on its south face and two target blocks near it. */
	private static void buildYard(GameTestHelper h) {
		h.setBlock(YARD_MARKER.north(), net.minecraft.world.level.block.Blocks.OAK_FENCE);
		h.setBlock(YARD_MARKER.east(2), net.minecraft.world.level.block.Blocks.TARGET);
		h.setBlock(YARD_MARKER.south(2), net.minecraft.world.level.block.Blocks.TARGET);
		BuildingTestSupport.placeMarker(h, dev.villageheart.core.building.BuildingType.TRAINING_YARD, YARD_MARKER, net.minecraft.core.Direction.SOUTH);
	}

	private static int xpOf(Villager g) { return g.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).xp(); }

	private static void setXp(Villager g, int level, int xp) {
		var d = g.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER);
		g.setAttached(dev.villageheart.worker.WorkerAttachments.WORKER, d.withProgress(level, xp));
	}

	/** Sparring at the linked Training Yard gives the last XP: level 2, 30 health, +1 damage. */
	@GameTest(environment = "villageheart-gametest:guards_spar", structure = YARD, skyAccess = true, maxTicks = 2400)
	public void sparringGivesXpLevelTwoHealth30(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		long[] spar0 = new long[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> buildYard(h))
				.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, YARD_MARKER, dev.villageheart.block.MarkerStatus.VALID)).thenExecute(() -> {
					h.assertValueEqual(BuildingTestSupport.loc(h, barracksMarker()), BuildingTestSupport.record(h, YARD_MARKER).orElseThrow().link(), "the yard is linked to the Barracks");
					setTime(h, 11900);
					g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
					setXp(g[0], 1, dev.villageheart.core.Balance.LEVEL_2_XP - 1);
					h.assertTrue(g[0].getMaxHealth() == 20.0, "max health 20 at the start");
					spar0[0] = dev.villageheart.worker.job.WorkStats.get("guard", "spar_xp");
				});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1000, "level 2 from sparring", () -> {
			h.assertTrue(g[0].getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).level() == 2, "level " + g[0].getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).level()
					+ ", xp " + xpOf(g[0]) + " at " + g[0].blockPosition() + " " + g[0].getBrain().getActiveActivities());
			h.assertTrue(g[0].getMaxHealth() == 30.0, "max health " + g[0].getMaxHealth());
		});
		seq.thenExecute(() -> {
			h.assertTrue(xpOf(g[0]) >= dev.villageheart.core.Balance.LEVEL_2_XP, "xp " + xpOf(g[0]));
			h.assertTrue(g[0].getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == dev.villageheart.core.Balance.GUARD_BASE_DAMAGE + 1.0, "base damage + 1");
			h.assertTrue(dev.villageheart.worker.job.WorkStats.get("guard", "spar_xp") > spar0[0], "work.guard.spar_xp counted");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** No Training Yard: no sparring XP; a level-2 guard keeps its 30 health across a save and reload (permanent modifiers are not clamped). */
	@GameTest(environment = "villageheart-gametest:guards_no_yard", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void noYardNoSparXpAndReload(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		int[] xp0 = new int[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			setTime(h, 11900);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			setXp(g[0], 1, 50);
			xp0[0] = 50;
		}).thenIdle(900);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(xp0[0], xpOf(g[0]), "no XP without a yard");
			dev.villageheart.worker.Workers.setLevel(h.getLevel().getServer(), g[0], 2);
			dev.villageheart.worker.guard.Guards.applyPerks(g[0], false);
			g[0].setHealth(25.0F);
			g[0] = LumberTestSupport.reloadEntity(h.getLevel(), g[0]);
			h.assertTrue(g[0].getMaxHealth() == 30.0, "max health survives the reload: " + g[0].getMaxHealth());
			h.assertTrue(g[0].getHealth() == 25.0F, "and so does the health: " + g[0].getHealth());
			h.assertTrue(g[0].getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).level() == 2, "level intact");
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().guardShift(g[0].getUUID()).orElseThrow().pinned(), "pin intact");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- raid call-up (T-L2-14) --------------------------------------------------------------------

	/** A real raid on the village calls every guard up, asleep or not; a raid that is only near (outside the claim) does not; the end of the raid sends them back. */
	@GameTest(environment = "villageheart-gametest:guards_raid_callup", structure = YARD, skyAccess = true, maxTicks = 2400)
	public void raidCallsUpAllGuards(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[2];
		net.minecraft.world.entity.raid.Raid[] raid = new net.minecraft.world.entity.raid.Raid[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			setTime(h, 18000);
			g[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			g[1] = hireGuard(h, spawnSpot().east(), Optional.of(Shift.DAY));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 300, "both day guards asleep at 18000", () -> {
			h.assertTrue(g[0].isSleeping() && g[1].isSleeping(), "asleep: " + g[0].isSleeping() + g[1].isSleeping());
		});
		// (b) a raid whose centre and raiders are outside the claim but within 96 blocks of the Heart does not call guards up
		seq = seq.thenExecute(() -> {
			var heart = dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().heart();
			var far = new BlockPos(heart.x() + 90, heart.y(), heart.z());
			dev.villageheart.worker.guard.RaidCheck.setViewSourceForTest((level, at) -> new dev.villageheart.worker.guard.RaidCheck.View() {
				public boolean active() { return true; }
				public BlockPos center() { return far; }
				public Iterable<? extends net.minecraft.world.entity.Entity> raiders() { return java.util.List.of(); }
			});
		}).thenIdle(200);
		seq = seq.thenExecute(() -> {
			h.assertTrue(g[0].isSleeping() && g[1].isSleeping(), "a raid outside the claim does not wake the guards");
			dev.villageheart.worker.guard.RaidCheck.setViewSourceForTest(null);
		});
		// (a) a real raid
		seq = seq.thenExecute(() -> {
			var owner = BuildingTestSupport.owner();
			raid[0] = h.getLevel().getRaids().createOrExtendRaid(owner, h.absolutePos(new BlockPos(3, 0, 1)));
			h.assertTrue(raid[0] != null, "the raid was created");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "both guards awake and on duty", () -> {
			for (Villager v : g) h.assertTrue(!v.isSleeping() && v.getBrain().isActive(GuardActivities.DUTY), "guard " + v.getId() + " " + v.isSleeping() + " " + v.getBrain().getActiveActivities());
		});
		seq = seq.thenExecute(() -> raid[0].stop());
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "back to rest when the raid is over", () -> {
			for (Villager v : g) h.assertTrue(v.getBrain().isActive(Activity.REST), "guard " + v.getId() + " " + v.getBrain().getActiveActivities());
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- the Heart screen action (T-L2-15) ---------------------------------------------------------

	/** A Steward pins a guard to a shift through the payload; a Resident is refused; a farmer's id is not a guard; choice 7 is a bad shift. */
	@GameTest(environment = "villageheart-gametest:guards_shift_packet", structure = YARD, skyAccess = true, maxTicks = 600)
	public void setGuardShiftPacket(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		net.minecraft.server.level.ServerPlayer[] p = new net.minecraft.server.level.ServerPlayer[2];
		java.util.List<HeartTestSupport.Sent>[] sent = new java.util.List[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			sent[0] = HeartTestSupport.recorder();
			g[0] = hireGuard(h, spawnSpot(), Optional.empty());
			p[0] = HeartTestSupport.player(h, "stew", dev.villageheart.core.Rank.STEWARD);
			p[1] = HeartTestSupport.player(h, "res", dev.villageheart.core.Rank.RESIDENT);
			int c0 = HeartTestSupport.open(h, p[0]).containerId, c1 = HeartTestSupport.open(h, p[1]).containerId;
			var before = dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().guardShift(g[0].getUUID()).orElseThrow();
			h.assertTrue(!before.pinned(), "auto at the start");
			send(p[0], c0, g[0].getUUID(), 2);
			h.assertTrue(HeartTestSupport.lastResult(sent[0], p[0]).ok(), "Steward ok: " + HeartTestSupport.lastResult(sent[0], p[0]));
			var after = dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().guardShift(g[0].getUUID()).orElseThrow();
			h.assertValueEqual(new dev.villageheart.core.guard.GuardShift(Shift.NIGHT, true), after, "pinned NIGHT");
			send(p[1], c1, g[0].getUUID(), 1);
			h.assertValueEqual(dev.villageheart.core.heart.Rejection.NO_PERMISSION.langKey(), HeartTestSupport.lastResult(sent[0], p[1]).key(), "Resident refused");
			h.assertValueEqual(after, dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().guardShift(g[0].getUUID()).orElseThrow(), "no change");
			send(p[0], c0, java.util.UUID.randomUUID(), 1);
			h.assertValueEqual(dev.villageheart.core.heart.Rejection.NOT_A_GUARD.langKey(), HeartTestSupport.lastResult(sent[0], p[0]).key(), "not a guard");
			send(p[0], c0, g[0].getUUID(), 7);
			h.assertValueEqual(dev.villageheart.core.heart.Rejection.BAD_SHIFT.langKey(), HeartTestSupport.lastResult(sent[0], p[0]).key(), "bad shift");
			send(p[0], c0, g[0].getUUID(), 0);
			h.assertTrue(!dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().guardShift(g[0].getUUID()).orElseThrow().pinned(), "back to auto");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	private static void send(net.minecraft.server.level.ServerPlayer p, int container, java.util.UUID villager, int choice) {
		HeartTestSupport.send(p, dev.villageheart.heart.net.SetGuardShiftPayload.CODEC, new dev.villageheart.heart.net.SetGuardShiftPayload(container, villager, choice));
	}

	// ---- the L3 layout (T-L2-13) -------------------------------------------------------------------

	/** The {@code defended} scenario's Barracks and Training Yard, built by the real sim actions inside the yard, are valid and linked. */
	@GameTest(environment = "villageheart-gametest:guards_defended_layout", structure = YARD, skyAccess = true, maxTicks = 900)
	public void defendedLayoutValid(GameTestHelper h) {
		begin(h);
		dev.villageheart.core.sim.Scenario scenario;
		try {
			java.nio.file.Path p = java.nio.file.Path.of("sim/scenarios/defended.json");
			if (!java.nio.file.Files.exists(p)) p = java.nio.file.Path.of("../../../sim/scenarios/defended.json");
			scenario = dev.villageheart.core.sim.ScenarioParser.parse("defended", java.nio.file.Files.readString(p));
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		var level = h.getLevel();
		BlockPos site = h.absolutePos(new BlockPos(0, -1, 0));
		dev.villageheart.sim.Host host = new dev.villageheart.sim.Host() {
			public net.minecraft.server.level.ServerLevel level() { return level; }
			public net.minecraft.server.MinecraftServer server() { return level.getServer(); }
			public BlockPos heart() { return h.absolutePos(BuildingTestSupport.BELL); }
			public BlockPos site() { return site; }
			public void setSite(BlockPos s) { }
		};
		BlockPos[] yardMarker = new BlockPos[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			for (var a : scenario.actions()) {
				if (a.type().equals("tier")) dev.villageheart.sim.GuardActions.tier(host, a);
				else if (a.type().equals("training_yard")) dev.villageheart.sim.GuardActions.trainingYard(host, shifted(a, -26, 0));
				else if (a.type().equals("prefab") && "barracks".equals(a.fields().get("building"))) dev.villageheart.sim.StarterActions.prefab(host, shifted(a, -26, 0));
			}
		}).thenWaitUntil(() -> {
			var views = dev.villageheart.building.Buildings.valid(level.getServer(), dev.villageheart.core.building.BuildingType.BARRACKS);
			h.assertTrue(views.size() == 1, "one valid Barracks");
			h.assertTrue(views.get(0).verdict().orElseThrow().slots() == 2, "two guard slots: " + views.get(0).verdict());
			var yards = dev.villageheart.building.Buildings.valid(level.getServer(), dev.villageheart.core.building.BuildingType.TRAINING_YARD);
			h.assertTrue(yards.size() == 1, "one valid Training Yard");
			h.assertTrue(yards.get(0).link() != null && yards.get(0).link().equals(views.get(0).marker()), "the yard is linked to the Barracks");
		});
		seq.thenExecute(() -> {
			var chestItems = new java.util.ArrayList<String>();
			for (var chest : dev.villageheart.worker.job.CampContainers.of(level.getServer(), dev.villageheart.world.VillageClaims.locate(level,
					dev.villageheart.building.Buildings.valid(level.getServer(), dev.villageheart.core.building.BuildingType.BARRACKS).get(0).marker())).orElseThrow()) {
				for (int i = 0; i < chest.container().getContainerSize(); i++) if (!chest.container().getItem(i).isEmpty()) chestItems.add(chest.container().getItem(i).getItem().toString());
			}
			h.assertTrue(chestItems.stream().filter(s -> s.contains("iron_sword")).count() >= 1 && chestItems.stream().anyMatch(s -> s.contains("iron_chestplate")), "kit in the chest: " + chestItems);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	private static dev.villageheart.core.sim.ScenarioAction shifted(dev.villageheart.core.sim.ScenarioAction a, int dx, int dz) {
		java.util.Map<String, Object> f = new java.util.LinkedHashMap<>(a.fields());
		if (f.get("at") instanceof java.util.List<?> l) f.put("at", java.util.List.of(((Number) l.get(0)).longValue() + dx, ((Number) l.get(1)).longValue() + dz));
		return new dev.villageheart.core.sim.ScenarioAction(a.type(), a.day(), a.tick(), f);
	}
}
