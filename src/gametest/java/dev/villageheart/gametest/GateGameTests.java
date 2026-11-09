package dev.villageheart.gametest;

import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.job.ToolTier;
import dev.villageheart.defence.GateKeeper;
import dev.villageheart.worker.Workers;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.core.Direction;

/**
 * Layer 2: hired workers and guards open and close the village's fence gates like doors, mobs still cannot (M18 gate step, human decision
 * 2026-11-09). The scene is a lumber camp with a cobblestone ring (3 high) 31 x 18 south of it; the only way in is a gate of three closed
 * oak fence gates in the ring's north wall (as M21's Gate places it), and an oak grows in the middle of the ring.
 */
public class GateGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	/** The ring: x 0..30, z 13..30 (big enough that the tree is out of the lumberjack's chopping range from outside the wall). The gate cells are x 14..16 of the north wall. */
	static final BlockPos TREE = new BlockPos(15, 0, 21);

	static BlockPos gate(int i) { return new BlockPos(14 + i, 0, 13); }

	static String env(String name) { return "villageheart-gametest:gates_" + name; }

	/** A closed ring of cobblestone with a three-wide gate in the north wall: closed gates, air above, a lintel on top. */
	static void ring(GameTestHelper h) {
		for (int x = 0; x <= 30; x++) for (int z = 13; z <= 30; z++) {
			boolean wall = x == 0 || x == 30 || z == 13 || z == 30;
			for (int y = 0; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), wall ? Blocks.COBBLESTONE : Blocks.AIR);
		}
		BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH).setValue(FenceGateBlock.OPEN, false);
		for (int i = 0; i < 3; i++) {
			h.setBlock(gate(i), gate);
			h.setBlock(gate(i).above(), Blocks.AIR);
		}
	}

	/** Is the entity inside the ring (x 1..29, z 14..29, in test coordinates; compared in world coordinates, whatever the structure's rotation)? */
	static boolean insideRing(GameTestHelper h, net.minecraft.world.entity.Entity e) {
		BlockPos a = h.absolutePos(new BlockPos(1, 0, 14)), b = h.absolutePos(new BlockPos(29, 0, 29));
		BlockPos p = e.blockPosition();
		return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
				&& p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
	}

	static boolean open(GameTestHelper h, int i) { return h.getBlockState(gate(i)).getValue(FenceGateBlock.OPEN); }

	static boolean anyOpen(GameTestHelper h) { return open(h, 0) || open(h, 1) || open(h, 2); }

	static double dist(GameTestHelper h, net.minecraft.world.entity.Entity e, BlockPos rel) {
		return e.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(rel)));
	}

	private static GameTestSequence scene(GameTestHelper h) {
		return LumberTestSupport.scene(h, false, true).thenExecute(() -> {
			ring(h);
			// grass inside the ring for the oak
			for (int x = 1; x <= 29; x++) for (int z = 14; z <= 29; z++) h.setBlock(new BlockPos(x, -1, z), Blocks.GRASS_BLOCK);
		});
	}

	// ---- a lumberjack whose only route to its trees is through a closed gate ----------------------

	/** The gate is open while the lumberjack passes and closed after; the lumberjack gets in, fells a log and the gate is never left open. */
	@GameTest(environment = "villageheart-gametest:gates_lumberjack", structure = YARD, skyAccess = true, maxTicks = 4200)
	public void lumberjackWalksThroughClosedGate(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		BlockPos[] base = new BlockPos[1];
		Set<BlockPos>[] logs = new Set[1];
		boolean[] sawOpenNear = new boolean[1], sawInside = new boolean[1], closedWhileInside = new boolean[1];
		long[] openSince = new long[1];
		String[] failure = new String[1];
		long[] t0 = new long[1];
		h.onEachTick(() -> {
			if (v[0] == null) return;
			boolean open = anyOpen(h);
			double d = Math.min(dist(h, v[0], gate(0)), Math.min(dist(h, v[0], gate(1)), dist(h, v[0], gate(2))));
			if (open && d <= 3.5) sawOpenNear[0] = true;
			if (insideRing(h, v[0])) {
				sawInside[0] = true;
				if (!open && sawOpenNear[0]) closedWhileInside[0] = true;
			}
			if (open && d > 6) {
				if (openSince[0] == 0) openSince[0] = h.getTick();
				if (h.getTick() - openSince[0] > 30 && failure[0] == null) failure[0] = "a gate stayed open for 30 ticks with the lumberjack " + d + " blocks away, at tick " + h.getTick();
			} else {
				openSince[0] = 0;
			}
		});
		GameTestSequence seq = scene(h).thenExecute(() -> {
			base[0] = growOak(h, TREE, 1L);
			logs[0] = logsOf(h, base[0]);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2600, "a log of the walled-in oak is felled", () -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			h.assertTrue(standing(h, logs[0]) < logs[0].size(), "no log felled yet; the lumberjack is at " + v[0].blockPosition() + " " + status(h, v[0])
					+ ", gates open " + open(h, 0) + open(h, 1) + open(h, 2));
		});
		seq.thenExecute(() -> {
			h.assertTrue(sawInside[0], "the lumberjack got inside the ring; it is at " + v[0].blockPosition() + ", logs standing "
					+ standing(h, logs[0]) + "/" + logs[0].size() + ", gates " + open(h, 0) + open(h, 1) + open(h, 2) + ", sawOpenNear " + sawOpenNear[0]);
			h.assertTrue(sawOpenNear[0], "a gate was open while the lumberjack was at it");
			h.assertTrue(closedWhileInside[0], "the gate was closed again while the lumberjack worked inside");
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			System.out.println("[m18] lumberjackWalksThroughClosedGate: first log after " + (h.getTick() - t0[0]) + " ticks");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- a guard patrols to a point behind a closed gate (replaces the plan's closedGateSkipsPoint) -----

	/**
	 * A Lamp Post stands inside the closed ring: the guard opens the gate, reaches the post's patrol point, the gate is closed behind it, and the
	 * guard walks out again (opening the gate again) and keeps patrolling.
	 */
	@GameTest(environment = "villageheart-gametest:guards_gate_patrol", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void closedGateIsOpenedByGuard(GameTestHelper h) {
		begin(h);
		Villager[] g = new Villager[1];
		BlockPos[] point = new BlockPos[1];
		boolean[] sawOpenNear = new boolean[1], closedWhileInside = new boolean[1];
		int[] arrivalsAtLamp = new int[1];
		long[] openSince = new long[1];
		String[] failure = new String[1];
		h.onEachTick(() -> {
			if (g[0] == null || point[0] == null) return;
			boolean open = anyOpen(h);
			double d = Math.min(dist(h, g[0], gate(0)), Math.min(dist(h, g[0], gate(1)), dist(h, g[0], gate(2))));
			if (open && d <= 3.5) sawOpenNear[0] = true;
			if (insideRing(h, g[0]) && !open && sawOpenNear[0]) closedWhileInside[0] = true;
			if (open && d > 6) {
				if (openSince[0] == 0) openSince[0] = h.getTick();
				if (h.getTick() - openSince[0] > 30 && failure[0] == null) failure[0] = "a gate stayed open for 30 ticks with the guard " + d + " blocks away, tick " + h.getTick();
			} else {
				openSince[0] = 0;
			}
		});
		GameTestSequence seq = GuardTestSupport.scene(h, 1).thenExecute(() -> {
			ring(h);
			GuardGameTests.placeLampPost(h, new BlockPos(15, -1, 21));
			GuardTestSupport.setTime(h, 2000);
			g[0] = GuardTestSupport.hireGuard(h, GuardTestSupport.spawnSpot(), java.util.Optional.of(dev.villageheart.core.guard.Shift.DAY));
			var points = dev.villageheart.defence.Defences.patrolPoints(h.getLevel().getServer());
			h.assertTrue(points.size() == 1, "one defence point: " + points);
			point[0] = points.get(0);
			h.assertTrue(insideRing(h, g[0]) == false, "the guard starts outside the ring");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1800, "the guard reaches the Lamp Post inside the ring", () -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			h.assertTrue(dev.villageheart.worker.guard.GuardRuntime.lastArrival(g[0].getUUID()).filter(point[0]::equals).isPresent(),
					"guard at " + g[0].blockPosition() + " mode " + dev.villageheart.worker.guard.GuardRuntime.modeOf(g[0].getUUID()) + ", point " + point[0]
							+ ", target " + dev.villageheart.worker.guard.GuardRuntime.patrolTarget(g[0].getUUID()) + ", unreachable "
							+ dev.villageheart.worker.guard.GuardRuntime.isUnreachable(g[0].getUUID(), point[0], h.getLevel().getGameTime()));
		});
		seq = seq.thenExecute(() -> {
			h.assertTrue(insideRing(h, g[0]), "the guard stands inside the ring at the post");
			h.assertTrue(sawOpenNear[0], "the gate was open while the guard passed");
			h.assertTrue(closedWhileInside[0], "the gate was closed behind the guard");
			arrivalsAtLamp[0] = dev.villageheart.worker.guard.GuardRuntime.arrivals(g[0].getUUID());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the guard walks out and reaches two more points", () -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			h.assertTrue(dev.villageheart.worker.guard.GuardRuntime.arrivals(g[0].getUUID()) >= arrivalsAtLamp[0] + 2, "arrivals "
					+ dev.villageheart.worker.guard.GuardRuntime.arrivals(g[0].getUUID()) + " at " + g[0].blockPosition());
		});
		seq.thenExecute(() -> {
			h.assertTrue(failure[0] == null, String.valueOf(failure[0]));
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- mobs still cannot -------------------------------------------------------------------------

	/** A hired worker can plan a path to a cell inside the ring, a zombie and an unemployed villager cannot; and not while a zombie stands near the gate. */
	@GameTest(environment = "villageheart-gametest:gates_mobs", structure = YARD, skyAccess = true, maxTicks = 900)
	public void mobsStillCannotPassTheGate(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Zombie[] z = new Zombie[2];
		BlockPos inside = new BlockPos(15, 0, 16);
		GameTestSequence seq = scene(h).thenExecute(() -> {
			v[0] = hire(h, new BlockPos(15, 0, 10));
			v[1] = WorkerTestSupport.spawnVillager(h, new BlockPos(17, 0, 10));
			z[0] = h.spawn(EntityType.ZOMBIE, new BlockPos(13, 0, 10));
			z[0].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			z[0].setPersistenceRequired();
			z[0].setNoAi(true); // only its navigation is asked
		});
		seq = seq.thenIdle(5);
		seq = seq.thenExecute(() -> {
			Path hired = v[0].getNavigation().createPath(h.absolutePos(inside), 1);
			Path plain = v[1].getNavigation().createPath(h.absolutePos(inside), 1);
			Path mob = z[0].getNavigation().createPath(h.absolutePos(inside), 1);
			h.assertTrue(plain == null || !plain.canReach(), "an unemployed villager cannot plan through the gate");
			h.assertTrue(mob == null || !mob.canReach(), "a zombie cannot plan through the gate");
			// the zombie is 3-4 blocks from the gate: a hostile near keeps the hired worker out as well
			h.assertTrue(hired == null || !hired.canReach(), "a hired worker does not plan through a gate with a hostile next to it");
			z[0].discard();
		});
		// the discarded zombie leaves the entity list at the end of the tick: ask again until the path is found
		seq = seq.thenWaitUntil(() -> {
			Path hired = v[0].getNavigation().createPath(h.absolutePos(inside), 1);
			h.assertTrue(hired != null && hired.canReach(), "without the zombie the hired worker plans through the gate");

			Path plain = v[1].getNavigation().createPath(h.absolutePos(inside), 1);
			h.assertTrue(plain == null || !plain.canReach(), "and the unemployed villager is still refused (the zombie is not what keeps it out)");
		});
		seq.thenExecute(() -> {
			h.assertTrue(!anyOpen(h), "planning does not open a gate");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- never left open ---------------------------------------------------------------------------

	/** A gate a lumberjack opened is closed when the lumberjack is fired and removed mid-walk, and after a "restart" (ledger kept, ages lost). */
	@GameTest(environment = "villageheart-gametest:gates_interrupt", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void gateIsNeverLeftOpen(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h).thenExecute(() -> {
			growOak(h, TREE, 1L);
			putInChest(h, chest(), 0, axe(ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2000, "the lumberjack opens a gate", () ->
				h.assertTrue(anyOpen(h), "no gate open yet; lumberjack at " + v[0].blockPosition()));
		seq = seq.thenExecute(() -> {
			h.assertTrue(GateKeeper.openGates(h.getLevel().getServer()) >= 1, "the keeper remembers the gate");
			Workers.fire(h.getLevel(), v[0]);
			v[0].discard(); // gone mid-walk
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the gate closes after the walker is gone", () -> {
			h.assertTrue(!anyOpen(h), "a gate is still open");
			h.assertValueEqual(0, GateKeeper.openGates(h.getLevel().getServer()), "open gates in the ledger");
		});
		// "reload": a gate open with only the saved ledger entry left (the ages are lost with the restart)
		seq = seq.thenExecute(() -> {
			BlockPos abs = h.absolutePos(gate(1));
			GateKeeper.open(h.getLevel(), abs, h.getLevel().getBlockState(abs), WorkerTestSupport.spawnVillager(h, new BlockPos(2, 0, 8)));
			h.assertTrue(open(h, 1), "opened by hand");
			GateKeeper.forgetAgesForTests();
		});
		seq.thenWaitUntil(() -> h.assertTrue(!anyOpen(h), "closed after the restart")).thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
