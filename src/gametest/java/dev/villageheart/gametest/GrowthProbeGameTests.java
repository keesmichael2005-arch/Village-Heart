package dev.villageheart.gametest;

import dev.villageheart.growth.BreedTrace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The breeding-abort probe of M23 (plan 3.5, steps 1 and 9). NOT part of the regular suite: unless {@code -Dvillageheart.probe=true} each
 * test passes at once. Run by hand:
 * {@code JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dvillageheart.probe=true -Dvillageheart.breed.trace=true
 * -Dfabric-api.gametest.filter=villageheart-gametest:growth_probe_game_tests_breeding_abort_probe" ./gradlew --no-daemon runGameTest}.
 *
 * <p>{@code PAIRS} fresh pairs of unemployed adults (3 bread each, so vanilla {@code canBreed} is true) stand in an open yard with a bed each;
 * the villager with the lower UUID gets {@code BREED_TARGET} (as vanilla's {@code InteractWith} does). A round ends when every run has ended
 * (with a baby or without), then everybody is discarded. {@code ROUNDS} rounds give {@code PAIRS * ROUNDS} runs. The reasons for the runs that
 * ended without a baby are counted by the {@link BreedTrace} stop hook.
 */
public class GrowthProbeGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	private static final int PAIRS = 20, ROUNDS = 5;

	private static boolean on() { return Boolean.getBoolean("villageheart.probe"); }

	private static BlockPos spot(int i) { return new BlockPos(3 + 6 * (i % 5), 0, 3 + 6 * (i / 5)); }

	@GameTest(environment = "villageheart-gametest:growth_probe", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void breedingAbortProbe(GameTestHelper h) { probe(h, false); }

	/** Step 9: the same pairs with the make-love fixes of a licensed pair (plan 3.5 a and b) switched on for everybody. */
	@GameTest(environment = "villageheart-gametest:growth_probe_licensed", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void breedingAbortProbeLicensed(GameTestHelper h) { probe(h, true); }

	private static void probe(GameTestHelper h, boolean licensed) {
		if (!on()) { h.succeed(); return; }
		VillageTestSupport.reset(h);
		dev.villageheart.growth.BreedingGate.setProbeAllLicensed(licensed);
		WorkerTestSupport.begin(h);
		BreedTrace.reset();
		List<Villager[]> pairs = new ArrayList<>();
		int[] unfinished = {0};
		GameTestSequence seq = h.startSequence().thenExecute(() -> {
			NeedsTestSupport.sweepVillagers(h);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
		});
		for (int r = 0; r < ROUNDS; r++) {
			int round = r;
			seq = seq.thenExecute(() -> {
				pairs.clear();
				for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.AIR);
				for (int i = 0; i < PAIRS; i++) {
					WorkerTestSupport.bed(h, spot(i).offset(0, 0, 3)); // one home bed each ...
					WorkerTestSupport.bed(h, spot(i).offset(2, 0, 3));
				}
				for (int x = 0; x < 32; x++) WorkerTestSupport.bed(h, new BlockPos(x, 0, 28)); // ... and spare beds for the babies
				h.getLevel().setDayTime(100);
			}).thenIdle(3).thenExecute(() -> {
				for (int i = 0; i < PAIRS; i++) {
					Villager a = WorkerTestSupport.spawnVillager(h, spot(i));
					Villager b = WorkerTestSupport.spawnVillager(h, spot(i).offset(2, 0, 0));
					for (Villager v : new Villager[] {a, b}) {
						v.setAge(0);
						v.getInventory().addItem(new ItemStack(Items.BREAD, 3));
					}
					NeedsTestSupport.claimBed(h, a, spot(i).offset(0, 0, 4)); // a villager with a home does not search for one
					NeedsTestSupport.claimBed(h, b, spot(i).offset(2, 0, 4));
					pairs.add(a.getUUID().compareTo(b.getUUID()) < 0 ? new Villager[] {a, b} : new Villager[] {b, a});
				}
			});
			// seed every 20 ticks while the initiator's run has not ended (the sensors need a few ticks before the partner is visible)
			for (int t = 0; t < 36; t++) {
				seq = seq.thenExecuteAfter(20, () -> {
					for (Villager[] p : pairs) {
						if (BreedTrace.finished(p[0].getUUID()) || !p[0].isAlive() || !p[1].isAlive()) continue;
						if (!p[0].getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET) && p[0].canBreed() && p[1].canBreed()) {
							p[0].getBrain().setMemory(MemoryModuleType.BREED_TARGET, p[1]);
						}
					}
				});
			}
			seq = seq.thenExecute(() -> {
				int open = 0;
				for (Villager[] p : pairs) if (!BreedTrace.finished(p[0].getUUID())) open++;
				unfinished[0] += open;
				System.out.println("[probe] round " + (round + 1) + "/" + ROUNDS + ": unfinished runs " + open + " | " + BreedTrace.summary());
				for (Entity e : h.getLevel().getEntities(EntityType.VILLAGER, new net.minecraft.world.phys.AABB(h.absolutePos(BlockPos.ZERO)).inflate(60), x -> true)) e.discard();
				WorkerTestSupport.discardSpawned(h.getLevel());
			});
		}
		seq.thenExecute(() -> {
			int runs = PAIRS * ROUNDS;
			dev.villageheart.growth.BreedingGate.setProbeAllLicensed(false);
			System.out.println("[probe] RESULT " + (licensed ? "LICENSED " : "PLAIN ") + "runs=" + runs + " unfinished=" + unfinished[0] + " " + BreedTrace.summary());
			WorkerTestSupport.end(h);
			VillageTestSupport.done(h);
		});
	}
}
