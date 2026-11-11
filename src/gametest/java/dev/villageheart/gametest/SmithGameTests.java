package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.golem.GolemGate;
import dev.villageheart.worker.gear.SelfCraftSwitch;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: the smith (M19). The spawn-gate tests are the first of them. Self-crafting is off (M19's rule for every smith test except
 * T-L2-5l and T-L2-5m) so a long scene cannot start a wood craft beside the assertion.
 */
public class SmithGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/**
	 * T-L2-3. Forty gossip rounds are the number of chances two in-game days would give (48 000 / 1 200). They are forty real attempts
	 * run back to back, not two days of simulated life. Each one is checked to be a would-spawn before it is offered to the gate.
	 */
	@GameTest(environment = "villageheart-gametest:smith_no_vanilla_golem", structure = YARD, skyAccess = true, maxTicks = 400)
	public void noVanillaGolemInClaim(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		List<Villager> pen = new ArrayList<>();
		BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off(); // M19: the gate test does not hire anyone, and self-crafting stays off anyway
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos centre = h.absolutePos(new BlockPos(16, 1, 16));
			SmithTestSupport.sweepGolems(level, centre, 24);
			SmithTestSupport.pad(level, centre, 11);
			int[][] at = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 0}, {0, 1}, {1, -1}, {1, 0}, {1, 1}, {2, 0}};
			for (int[] o : at) pen.add(SmithTestSupport.villagerAt(level, centre.offset(o[0], 0, o[1])));
			GolemGate.reset();
			long t = Math.max(level.getGameTime(), 1200L) + 1201L;
			for (int round = 0; round < 40; round++) {
				assertWouldSpawn(h, pen, t);
				for (int p = 0; p < 5; p++) pen.get(p * 2).gossip(level, pen.get(p * 2 + 1), t);
				t += 1201L;
			}
			assertWouldSpawn(h, pen, t);
			for (int i = 0; i < 10; i++) pen.get(0).spawnGolemIfNeeded(level, t, 3);
			h.assertTrue(SmithTestSupport.golemsNear(level, centre, 24) == 0, "no vanilla golem inside the claim");
			h.assertValueEqual(210, GolemGate.calls(), "every gossip and panic call reached the gate");
			h.assertValueEqual(210, GolemGate.blocked(), "every one of those was a real would-spawn");
			// control: the same pen, the claim released, one more round. Vanilla does spawn here.
			VillageClaims.releaseAt(level, new BlockPos(heart.x(), heart.y(), heart.z()));
			t += 1201L;
			assertWouldSpawn(h, pen, t);
			for (int p = 0; p < 5; p++) pen.get(p * 2).gossip(level, pen.get(p * 2 + 1), t);
			h.assertTrue(SmithTestSupport.golemsNear(level, centre, 16) >= 1, "vanilla spawns once the claim is gone");
			SmithTestSupport.sweepGolems(level, centre, 24);
			System.out.println("[m19] noVanillaGolemInClaim: 210 calls blocked, then a golem after release");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** T-L2-3b. 84 blocks is outside the 80-block sphere and inside the 8-block margin. 100 blocks is not. */
	@GameTest(environment = "villageheart-gametest:smith_gate_edge", structure = YARD, skyAccess = true, maxTicks = 400)
	public void gateEdgeOfClaim(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off(); // M19
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos near = new BlockPos(heart.x() + 84, heart.y(), heart.z());
			BlockPos far = new BlockPos(heart.x() + 100, heart.y(), heart.z());
			// the chunks have to tick once before they will accept entities; a forced chunk is not in the entity tracker on the tick it was loaded
			SmithTestSupport.force(level, near, 16);
			SmithTestSupport.force(level, far, 16);
		}).thenIdle(5).thenExecute(() -> {
			ServerLevel level = h.getLevel();
			HeartLocation heart = VillageClaims.current(level.getServer()).orElseThrow().heart();
			BlockPos near = new BlockPos(heart.x() + 84, heart.y(), heart.z());
			BlockPos far = new BlockPos(heart.x() + 100, heart.y(), heart.z());
			trySpawnAt(h, level, near, false);
			trySpawnAt(h, level, far, true);
			SmithTestSupport.sweepGolems(level, near, 20);
			SmithTestSupport.sweepGolems(level, far, 20);
			SmithTestSupport.releaseForced(level);
			System.out.println("[m19] gateEdgeOfClaim: blocked at 84, spawned at 100");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** T-L2-9. Four iron blocks and a carved pumpkin are the player's golem, and the villager gate does not see that path. */
	@GameTest(environment = "villageheart-gametest:smith_player_golem", structure = YARD, skyAccess = true, maxTicks = 200)
	public void playerBuiltGolemStillWorks(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		BlockPos centre = new BlockPos(16, 1, 12);
		BuildingTestSupport.begin(h).thenExecute(() -> {
			SelfCraftSwitch.off(); // M19
			ServerLevel level = h.getLevel();
			SmithTestSupport.sweepGolems(level, h.absolutePos(centre), 8);
			// top ~^~ , middle ### , bottom ~#~ ; the pumpkin goes on last, which is what runs CarvedPumpkinBlock.onPlace
			h.setBlock(centre, Blocks.IRON_BLOCK);
			h.setBlock(centre.above().west(), Blocks.IRON_BLOCK);
			h.setBlock(centre.above(), Blocks.IRON_BLOCK);
			h.setBlock(centre.above().east(), Blocks.IRON_BLOCK);
			h.setBlock(centre.above(2), Blocks.CARVED_PUMPKIN);
		}).thenIdle(20).thenExecute(() -> {
			h.assertTrue(SmithTestSupport.golemsNear(h.getLevel(), h.absolutePos(centre), 6) >= 1, "the pumpkin built a golem");
			SmithTestSupport.sweepGolems(h.getLevel(), h.absolutePos(centre), 8);
			System.out.println("[m19] playerBuiltGolemStillWorks: pumpkin golem appeared");
			VillageTestSupport.done(h);
			WorkerTestSupport.end(h);
		});
	}

	/** Five villagers on a pad, all willing, and one panic-path call. {@code expectGolem} is the control outside the margin. */
	private static void trySpawnAt(GameTestHelper h, ServerLevel level, BlockPos centre, boolean expectGolem) {
		SmithTestSupport.pad(level, centre, 11);
		SmithTestSupport.sweepGolems(level, centre, 20);
		h.assertTrue(level.getBlockState(centre.below()).is(Blocks.STONE), "the pad is stone, saw " + level.getBlockState(centre.below()));
		h.assertTrue(level.getBlockState(centre).isAir() && level.getBlockState(centre.above(3)).isAir(), "the pad has air above it");
		List<Villager> pen = new ArrayList<>();
		for (int i = 0; i < 5; i++) pen.add(SmithTestSupport.villagerAt(level, centre.offset(i - 2, 0, 0)));
		int inWorld = level.getEntities(net.minecraft.world.entity.EntityType.VILLAGER, new net.minecraft.world.phys.AABB(centre).inflate(10), net.minecraft.world.entity.npc.villager.Villager::isAlive).size();
		h.assertTrue(inWorld >= 5, "the five villagers are in the world, saw " + inWorld);
		long t = Math.max(level.getGameTime(), 1200L) + 1201L;
		assertWouldSpawn(h, pen, t);
		int calls = GolemGate.calls();
		pen.get(2).spawnGolemIfNeeded(level, t, 3);
		h.assertTrue(GolemGate.calls() == calls + 1, "the panic call reached the gate at " + centre);
		int found = SmithTestSupport.golemsNear(level, centre, 16);
		if (expectGolem) h.assertTrue(found >= 1, "a golem spawns at " + centre);
		else h.assertTrue(found == 0, "the margin still blocks at " + centre);
		for (Villager v : pen) v.discard();
	}

	/** What the plan requires before every round: at least five want a golem, and nobody has just seen one. */
	private static void assertWouldSpawn(GameTestHelper h, List<Villager> pen, long now) {
		int willing = 0;
		for (Villager v : pen) {
			v.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, v.level().getGameTime());
			v.getBrain().eraseMemory(MemoryModuleType.GOLEM_DETECTED_RECENTLY);
			h.assertTrue(!v.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY), "no recent golem");
			if (v.wantsToSpawnGolem(now)) willing++;
		}
		h.assertTrue(willing >= 5, "at least five villagers would spawn a golem, got " + willing);
	}
}
