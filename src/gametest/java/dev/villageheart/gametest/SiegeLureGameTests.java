package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildTestSupport.YARD;

import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.siege.SiegeRecord;
import dev.villageheart.gametest.BuildTestSupport.With;
import dev.villageheart.siege.LureSlots;
import dev.villageheart.siege.PathBudget;
import dev.villageheart.siege.SiegeDamage;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Zombie lure tests (docs/plans/M25.md step 13). Same rules as {@link SiegeGameTests}: one environment, walls through the builder.
 * The ring is a 13 by 13 interior so the seen cells clear {@code LURE_MIN_WALL_CELLS}.
 */
public class SiegeLureGameTests {
	private static final int X0 = -6, X1 = 8, Z0 = -6, Z1 = 8;

	private static BlockPos rel(int x, int y, int z) { return new BlockPos(x, y, z); }

	/** T-L2-20. A zombie with no target crosses a closed ring at night and leaves a breach. */
	@GameTest(environment = "villageheart-gametest:siege_zombie_wall", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void zombieBehindClosedWallBreaks(GameTestHelper h) {
		Zombie[] zombie = new Zombie[1];
		Mob[] villager = new Mob[1];
		long[] t0 = new long[1];
		boolean[] targeted = new boolean[1];
		var seq = ring(h, Ring.CLOSED).thenExecute(() -> {
			villager[0] = h.spawn(EntityType.VILLAGER, rel(6, 0, 2));
			villager[0].setNoAi(true);
			h.spawn(EntityType.VILLAGER, rel(5, 0, 4)).setNoAi(true);
			seeWalls(h);
			zombie[0] = zombie(h, rel(6, 0, Z0 - 30), false);
			h.assertTrue(!zombie[0].getSensing().hasLineOfSight(villager[0]), "no line of sight from outside");
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> {
			if (zombie[0].getTarget() != null) targeted[0] = true;
			h.assertTrue(!targeted[0], "the zombie took a target " + zombie[0].getTarget());
			h.assertTrue(nearDefence(h, zombie[0], 2), "not beside a defence cell " + at(h, zombie[0]));
		}).thenExecute(() -> h.assertTrue(h.getTick() - t0[0] <= 1200, "beside the wall took " + (h.getTick() - t0[0])));
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(maxDamage(h) > 0, "no damage yet at " + at(h, zombie[0]) + " target " + zombie[0].getTarget());
		}).thenExecute(() -> h.assertTrue(h.getTick() - t0[0] <= 1800, "first damage took " + (h.getTick() - t0[0])));
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(anyBreach(h), "no breach yet at " + at(h, zombie[0]) + " target " + zombie[0].getTarget());
			BlockPos p = at(h, zombie[0]);
			h.assertTrue(p.getX() > X0 && p.getX() < X1 && p.getZ() > Z0 && p.getZ() < Z1, "not inside " + p);
		}).thenExecute(() -> {
			h.assertTrue(h.getTick() - t0[0] <= 3600, "inside took " + (h.getTick() - t0[0]));
			VillageTestSupport.done(h);
		});
	}

	/** T-L2-21. A skeleton, then a spider, never grows a mod goal and never closes on the ring. */
	@GameTest(environment = "villageheart-gametest:siege_skeleton", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void skeletonStaysVanilla(GameTestHelper h) {
		Mob[] mob = new Mob[1];
		long[] t0 = new long[1];
		var seq = ring(h, Ring.CLOSED).thenExecute(() -> {
			seeWalls(h);
			mob[0] = h.spawn(EntityType.SKELETON, rel(1, 0, Z0 - 30));
			mob[0].setPersistenceRequired();
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> vanilla(h, mob[0], t0[0]));
		seq = seq.thenExecute(() -> {
			mob[0].discard();
			mob[0] = h.spawn(EntityType.SPIDER, rel(1, 0, Z0 - 30));
			mob[0].setPersistenceRequired();
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> vanilla(h, mob[0], t0[0])).thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-22. Daytime does not lure. A running siege does, within 100 ticks. */
	@GameTest(environment = "villageheart-gametest:siege_lure_day", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void lureOffByDay(GameTestHelper h) {
		Zombie[] zombie = new Zombie[1];
		long[] t0 = new long[1];
		var seq = ring(h, Ring.CLOSED).thenExecute(() -> {
			h.getLevel().setDayTime(3000);
			seeWalls(h);
			zombie[0] = zombie(h, rel(1, 0, Z0 - 30), true);
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertTrue(!LureSlots.holds(h.getLevel().getServer(), zombie[0]), "lured by day");
			h.assertTrue(maxDamage(h) == 0, "damage by day");
			h.assertTrue(h.getTick() - t0[0] >= 1200, "still day");
		});
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			VillageClaims.data(server).update(v -> v.withThreats(v.threats().withSiege(Optional.of(new SiegeRecord(0, List.of(), 0, 0, 0)))));
			LureSlots.resetForTests();
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> h.assertTrue(LureSlots.holds(h.getLevel().getServer(), zombie[0]), "the siege has not lured it"))
				.thenExecute(() -> {
					h.assertTrue(h.getTick() - t0[0] <= 100, "lure after the siege took " + (h.getTick() - t0[0]));
					VillageTestSupport.done(h);
				});
	}

	/** T-L2-23. Lamps are not a wall. Night, villagers, and no recorded wall: the lure stays off. */
	@GameTest(environment = "villageheart-gametest:siege_no_walls", structure = YARD, skyAccess = true, maxTicks = 2200)
	public void noWallsNoLure(GameTestHelper h) {
		Zombie[] zombie = new Zombie[1];
		long[] t0 = new long[1];
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			var server = h.getLevel().getServer();
			server.setDifficulty(Difficulty.NORMAL, true);
			h.getLevel().setDayTime(18000);
			BuildTestSupport.stock(h, DefenceTestSupport.lampMaterials(1));
			BuildTestSupport.startBuild(h, dev.villageheart.core.blueprint.Blueprints.LAMP_POST, rel(6, -1, 6), dev.villageheart.core.blueprint.Rotation4.NONE);
			DefenceTestSupport.advanceAll(h);
			h.spawn(EntityType.VILLAGER, rel(3, 0, 3)).setNoAi(true);
			h.spawn(EntityType.VILLAGER, rel(5, 0, 3)).setNoAi(true);
			floor(h, -8, 16, -50, 16);
			parkOwner(h);
			zombie[0] = zombie(h, rel(1, 0, -40), false);
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> {
			h.assertTrue(!LureSlots.on(h.getLevel().getServer()), "the lure started without a wall");
			h.assertTrue(!LureSlots.holds(h.getLevel().getServer(), zombie[0]), "the zombie was lured");
			h.assertTrue(h.getTick() - t0[0] >= 1200, "still watching");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-24. At most 24 zombies are lured, and at most 2 siege paths run in one tick. Discarding some frees the slots. */
	@GameTest(environment = "villageheart-gametest:siege_lure_budget", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void lureBudgetCaps(GameTestHelper h) {
		List<Zombie> zombies = new ArrayList<>();
		long[] t0 = new long[1];
		int[] paths = new int[1];
		var seq = ring(h, Ring.CLOSED).thenExecute(() -> {
			seeWalls(h);
			for (int i = 0; i < 30; i++) zombies.add(zombie(h, rel(1, 0, Z0 - 60 - i), false));
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> {
			var server = h.getLevel().getServer();
			paths[0] = Math.max(paths[0], PathBudget.usedThisTick(server));
			h.assertTrue(paths[0] <= 2, "siege paths in one tick " + paths[0]);
			h.assertTrue(holding(h, zombies) <= 24, "lured " + holding(h, zombies));
			h.assertTrue(h.getTick() - t0[0] >= 200 && holding(h, zombies) == 24, "waiting for 24");
		});
		seq = seq.thenExecute(() -> {
			int dropped = 0;
			for (Zombie zombie : zombies) {
				if (dropped >= 10) break;
				if (!LureSlots.holds(h.getLevel().getServer(), zombie)) continue;
				zombie.discard();
				dropped++;
			}
			h.assertTrue(dropped == 10, "discarded " + dropped);
			LureSlots.resetForTests();
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> {
			int live = 0;
			for (Zombie zombie : zombies) if (zombie.isAlive()) live++;
			h.assertTrue(holding(h, zombies) == live, "slots free, holding " + holding(h, zombies) + " of " + live);
			h.assertTrue(h.getTick() - t0[0] <= 100, "reuse took " + (h.getTick() - t0[0]));
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-37, zombie half. A trench stops the zombie on the edge. The creeper half is step 14. */
	@GameTest(environment = "villageheart-gametest:siege_trench", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void trenchInFrontOfWall(GameTestHelper h) {
		Zombie[] zombie = new Zombie[1];
		long[] t0 = new long[1];
		var seq = ring(h, Ring.TRENCH).thenExecute(() -> {
			seeWalls(h);
			zombie[0] = zombie(h, rel(1, 0, Z0 - 30), false);
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> {
			h.assertTrue(maxDamage(h) == 0, "the trench let a hit through");
			BlockPos p = at(h, zombie[0]);
			h.assertTrue(p.getZ() >= Z0 - 4, "has not reached the edge " + p);
			h.assertTrue(h.getTick() - t0[0] >= 1800, "still at the trench");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-38. A three-wide gap is still enough wall to lure, and the zombie walks through it. */
	@GameTest(environment = "villageheart-gametest:siege_gap", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void openGapIsUsed(GameTestHelper h) {
		Zombie[] zombie = new Zombie[1];
		long[] t0 = new long[1];
		var seq = ring(h, Ring.GAP).thenExecute(() -> {
			h.spawn(EntityType.VILLAGER, rel(2, 0, 2)).setNoAi(true);
			seeWalls(h);
			h.assertTrue(seen(h) >= 150, "the gap still lures: " + seen(h));
			zombie[0] = zombie(h, rel(-4, 0, Z0 - 20), false);
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> {
			h.assertTrue(maxDamage(h) == 0, "the gap was damaged");
			BlockPos p = at(h, zombie[0]);
			h.assertTrue(p.getZ() > Z0 && p.getZ() < Z1, "not through the gap " + p);
			h.assertTrue(h.getTick() - t0[0] <= 900, "through the gap took " + (h.getTick() - t0[0]));
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	/** T-L2-39. One short wall is not an enclosure. The lure stays off and the breach goal does not run. */
	@GameTest(environment = "villageheart-gametest:siege_partial_wall", structure = YARD, skyAccess = true, maxTicks = 2200)
	public void partialWallNoLure(GameTestHelper h) {
		Zombie[] zombie = new Zombie[1];
		long[] t0 = new long[1];
		var seq = BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			var server = h.getLevel().getServer();
			server.setDifficulty(Difficulty.HARD, true);
			h.getLevel().setDayTime(18000);
			BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 64));
			DefenceTestSupport.startLine(h, "wall_segment", rel(2, -1, 18), rel(11, -1, 18));
			DefenceTestSupport.advanceAll(h);
			h.setBlock(rel(6, 0, 12), Blocks.OAK_DOOR.defaultBlockState());
			h.setBlock(rel(6, 1, 12), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
			h.spawn(EntityType.VILLAGER, rel(6, 0, 8)).setNoAi(true);
			floor(h, -4, 20, -40, 24);
			parkOwner(h);
			seeWalls(h);
			zombie[0] = zombie(h, rel(6, 0, 18 - 50), false);
			t0[0] = h.getTick();
		});
		seq.thenWaitUntil(() -> {
			h.assertTrue(!LureSlots.on(h.getLevel().getServer()), "a short wall lured");
			h.assertTrue(!breachRunning(zombie[0]), "the breach goal ran");
			h.assertTrue(h.getTick() - t0[0] >= 1200, "still watching");
		}).thenExecute(() -> VillageTestSupport.done(h));
	}

	private enum Ring { CLOSED, GAP, TRENCH }

	private static net.minecraft.gametest.framework.GameTestSequence ring(GameTestHelper h, Ring ring) {
		return BuildTestSupport.layout(h, With.GRID).thenExecute(() -> {
			h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			h.getLevel().setDayTime(14000);
			lureLayout(h, ring);
			parkOwner(h);
		});
	}

	private static void vanilla(GameTestHelper h, Mob mob, long t0) {
		h.assertTrue(maxDamage(h) == 0, "vanilla mob damaged a wall");
		h.assertTrue(siegeGoals(mob) == 0, "a mod goal was installed");
		h.assertTrue(!nearDefence(h, mob, 8), "within 8 of the ring " + at(h, mob));
		h.assertTrue(h.getTick() - t0 >= 1800, "still watching");
	}

	/**
	 * 15 by 15 outer ring. The gate is on the north, so a zombie walking up from the south meets solid cobble and cannot
	 * see a villager through the gate's eye-level air. A gap drops three south columns. A trench is the outside face, two deep.
	 */
	private static void lureLayout(GameTestHelper h, Ring ring) {
		// Wide enough that a 16-block lure waypoint from the south approach stays on forced ground (storage is x >= 27).
		floor(h, X0 - 20, X1 + 16, Z0 - 100, Z1 + 6);
		BuildTestSupport.stock(h, new ItemStack(Items.COBBLESTONE, 320), new ItemStack(Items.OAK_FENCE_GATE, 3));
		DefenceTestSupport.startLine(h, "wall_segment", rel(X0, -1, Z1), rel(-2, -1, Z1));
		DefenceTestSupport.startLine(h, "gate", rel(-1, -1, Z1), rel(3, -1, Z1));
		DefenceTestSupport.startLine(h, "wall_segment", rel(4, -1, Z1), rel(X1, -1, Z1));
		DefenceTestSupport.startLine(h, "wall_segment", rel(X0, -1, Z0), rel(X0, -1, Z1 - 1));
		DefenceTestSupport.startLine(h, "wall_segment", rel(X1, -1, Z0), rel(X1, -1, Z1 - 1));
		// A gap test leaves three south columns unbuilt (nine cells). 165 - 9 = 156, still enough to lure.
		if (ring == Ring.GAP) DefenceTestSupport.startLine(h, "wall_segment", rel(-2, -1, Z0), rel(X1 - 1, -1, Z0));
		else DefenceTestSupport.startLine(h, "wall_segment", rel(X0 + 1, -1, Z0), rel(X1 - 1, -1, Z0));
		DefenceTestSupport.advanceAll(h);
		if (ring == Ring.TRENCH) trench(h);
		h.assertTrue(VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences().size() >= 4, "the ring is recorded");
	}

	private static void trench(GameTestHelper h) {
		for (int x = X0 - 1; x <= X1 + 1; x++) {
			dig(h, x, Z0 - 1);
			dig(h, x, Z1 + 1);
		}
		for (int z = Z0; z <= Z1; z++) {
			dig(h, X0 - 1, z);
			dig(h, X1 + 1, z);
		}
	}

	private static void dig(GameTestHelper h, int x, int z) {
		h.setBlock(rel(x, 0, z), Blocks.AIR.defaultBlockState());
		h.setBlock(rel(x, -1, z), Blocks.AIR.defaultBlockState());
		h.setBlock(rel(x, -2, z), Blocks.STONE.defaultBlockState());
	}

	private static void floor(GameTestHelper h, int x0, int x1, int z0, int z1) {
		BlockPos a = h.absolutePos(rel(x0, 0, z0));
		BlockPos b = h.absolutePos(rel(x1, 0, z1));
		int cx0 = Math.min(a.getX(), b.getX()) >> 4, cx1 = Math.max(a.getX(), b.getX()) >> 4;
		int cz0 = Math.min(a.getZ(), b.getZ()) >> 4, cz1 = Math.max(a.getZ(), b.getZ()) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) h.getLevel().setChunkForced(cx, cz, true);
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			if (x >= 27) continue;
			h.setBlock(rel(x, -1, z), Blocks.STONE.defaultBlockState());
			for (int y = 0; y <= 3; y++) {
				if ((x == 1 || x == 3) && z == 1) continue;
				h.setBlock(rel(x, y, z), Blocks.AIR.defaultBlockState());
			}
		}
	}

	private static void parkOwner(GameTestHelper h) {
		var owner = BuildingTestSupport.owner();
		BlockPos at = h.absolutePos(rel(1, 2, 70));
		h.setBlock(rel(1, 1, 70), Blocks.STONE.defaultBlockState());
		owner.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	private static void seeWalls(GameTestHelper h) {
		var server = h.getLevel().getServer();
		SiegeDamage.scan(server, VillageClaims.current(server).orElseThrow(), -1);
		LureSlots.resetForTests();
	}

	private static Zombie zombie(GameTestHelper h, BlockPos at, boolean helmet) {
		Zombie zombie = h.spawn(EntityType.ZOMBIE, at);
		zombie.setPersistenceRequired();
		if (helmet) zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		return zombie;
	}

	private static int seen(GameTestHelper h) {
		int n = 0;
		for (DefenceSite site : VillageClaims.current(h.getLevel().getServer()).orElseThrow().defences()) {
			if (site.blueprintId().equals("wall_segment") || site.blueprintId().equals("gate")) n += site.seen().size();
		}
		return n;
	}

	private static int holding(GameTestHelper h, List<Zombie> zombies) {
		var server = h.getLevel().getServer();
		int n = 0;
		for (Zombie zombie : zombies) if (zombie.isAlive() && LureSlots.holds(server, zombie)) n++;
		return n;
	}

	private static int maxDamage(GameTestHelper h) {
		var server = h.getLevel().getServer();
		var village = VillageClaims.current(server).orElse(null);
		if (village == null) return 0;
		var damage = SiegeDamage.damage(server);
		int max = 0;
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) max = Math.max(max, damage.damageAt(cell.packed()));
		return max;
	}

	private static boolean anyBreach(GameTestHelper h) {
		var server = h.getLevel().getServer();
		var village = VillageClaims.current(server).orElse(null);
		if (village == null) return false;
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) {
			if (SiegeDamage.isBreached(server, cell.packed())) return true;
		}
		return false;
	}

	private static boolean nearDefence(GameTestHelper h, Mob mob, int blocks) {
		var village = VillageClaims.current(h.getLevel().getServer()).orElse(null);
		if (village == null) return false;
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) {
			int dx = Math.abs(cell.pos().x() - mob.getBlockX());
			int dy = Math.abs(cell.pos().y() - mob.getBlockY());
			int dz = Math.abs(cell.pos().z() - mob.getBlockZ());
			if (Math.max(dx, Math.max(dy, dz)) <= blocks) return true;
		}
		return false;
	}

	private static boolean breachRunning(Mob mob) {
		for (var wrapped : ((dev.villageheart.mixin.MobGoalsAccessor) mob).villageheart$goalSelector().getAvailableGoals()) {
			if (wrapped.getGoal() instanceof dev.villageheart.siege.BreachGoal && wrapped.isRunning()) return true;
		}
		return false;
	}

	private static int siegeGoals(Mob mob) {
		int n = 0;
		for (var wrapped : ((dev.villageheart.mixin.MobGoalsAccessor) mob).villageheart$goalSelector().getAvailableGoals()) {
			var goal = wrapped.getGoal();
			if (goal instanceof dev.villageheart.siege.BreachGoal || goal instanceof dev.villageheart.siege.VillageLureGoal
					|| goal instanceof dev.villageheart.siege.CreeperBreachGoal) n++;
		}
		return n;
	}

	private static BlockPos at(GameTestHelper h, Mob mob) { return VillageTestSupport.rel(h, mob.blockPosition()); }
}
