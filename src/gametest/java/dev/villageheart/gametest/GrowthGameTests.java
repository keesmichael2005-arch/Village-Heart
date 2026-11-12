package dev.villageheart.gametest;

import static dev.villageheart.gametest.GrowthTestSupport.*;

import dev.villageheart.core.growth.LicenceState;
import dev.villageheart.core.growth.Reason;
import dev.villageheart.growth.BreedTrace;
import dev.villageheart.growth.Growth;
import dev.villageheart.growth.GrowthStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Layer 2: population growth (M23). Rules: see {@link GrowthTestSupport}. Every test has its own environment (its own batch) because the day
 * clock is shared and these tests move it.
 */
public class GrowthGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static void assertPoiSpace(GameTestHelper h, BlockPos headRel, boolean space, String what) {
		h.assertTrue(WorkerTestSupport.poiHasSpace(h, headRel) == space, what + (space ? " has a free ticket" : " is taken"));
	}

	// ---- T-L2-1 ---------------------------------------------------------------------------------

	/** Roadmap L2: a free House bed and food give a baby within two days; the cooldown stops a second one the next day. */
	@GameTest(environment = "villageheart-gametest:growth_free_bed_baby", structure = YARD, skyAccess = true, maxTicks = 6000)
	public void freeBedAndFoodGiveBabyWithinTwoDays(GameTestHelper h) {
		Villager[] v = new Villager[2];
		long[] dawn = new long[2];
		GameTestSequence seq = scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 5000);
			v[0] = adult(h, HOUSE1, 1, 0);
			v[1] = adult(h, HOUSE1, 3, 0);
			home(h, v[0], bedHead(HOUSE1, 0));
			home(h, v[1], bedHead(HOUSE1, 1));
			housingNow(h);
			h.assertTrue(housed(h, v[0]) && housed(h, v[1]), "both adults are housed");
			h.assertFalse(v[0].canBreed() || v[1].canBreed(), "they carry no food: vanilla alone could not breed");
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			GrowthStats.Dawn d = GrowthStats.lastDawn();
			h.assertTrue(d != null && d.reason() == Reason.LICENSED, "the dawn licensed a pair: " + describe(h));
			var l = licence(h).orElseThrow(() -> h.assertionException("a licence exists"));
			h.assertTrue(l.pair().contains(v[0].getUUID()) && l.pair().contains(v[1].getUUID()), "the licence is for exactly these two");
			h.assertTrue(l.bed().isPresent(), "a bed is reserved");
			h.assertFalse(spaceAbs(h, BlockPos.of(l.bed().getAsLong())), "the reserved bed is held from the dawn on");
			dawn[0] = h.getLevel().getDayTime();
			log("T-L2-1: dawn licensed at day time " + dawn[0]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1500, "the baby", () -> h.assertTrue(!babies(h).isEmpty(), "no baby yet: " + describe(h)));
		seq = seq.thenExecute(() -> {
			var l = licence(h).orElseThrow();
			h.assertTrue(l.state() == LicenceState.BORN, "the licence is BORN: " + describe(h));
			Villager baby = babies(h).get(0);
			h.assertValueEqual(1, babies(h).size(), "one baby");
			h.assertTrue(baby.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().asLong() == l.bed().getAsLong()).isPresent(),
					"the baby's HOME is the reserved bed");
			for (Villager p : v) h.assertTrue(p.getAge() > 5900 && p.getAge() <= 6000, "the parent is on cooldown: age " + p.getAge());
			h.assertValueEqual(1, GrowthStats.births(), "the birth hook counted one");
			h.assertValueEqual(1, loadedBabies(), "the entity-load listener saw one baby");
			h.assertValueEqual(0, GrowthStats.unlicensedBirths(), "no unlicensed birth");
			h.assertTrue(Growth.state(h.getLevel().getServer()).lastBirthDay().isPresent(), "lastBirthDay is set");
			h.assertValueEqual(1L, Growth.state(h.getLevel().getServer()).birthsTotal(), "birthsTotal");
			log("T-L2-1: baby born " + (h.getLevel().getDayTime() - dawn[0]) + " ticks after the dawn, aborts " + GrowthStats.aborts() + " " + BreedTrace.summary());
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			GrowthStats.Dawn d = GrowthStats.lastDawn();
			h.assertTrue(d.reason() == Reason.TOO_FEW_READY, "the second dawn: parents are on cooldown, not out of beds: " + d);
			h.assertTrue(d.freeBeds() >= 1, "a bed is still free: " + d);
		});
		seq = seq.thenIdle(1500).thenExecute(() -> {
			h.assertValueEqual(1, loadedBabies(), "still one baby after the second morning");
			end(h);
		});
	}

	// ---- T-L2-6, T-L2-9 ------------------------------------------------------------------------

	/** An adult whose bed is outside any House cannot be a parent. */
	@GameTest(environment = "villageheart-gametest:growth_unhoused", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void unhousedAdultCannotBreed(GameTestHelper h) {
		Villager[] v = new Villager[2];
		BlockPos outFoot = new BlockPos(24, 0, 24);
		GameTestSequence seq = scene(h, 4, 0, 32).thenExecute(() -> WorkerTestSupport.bed(h, outFoot))
				.thenWaitUntil(() -> h.assertTrue(WorkerTestSupport.poiPresent(h, outFoot.south()), "the outside bed's POI exists"))
				.thenExecute(() -> {
					h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 5000);
					v[0] = adult(h, HOUSE1, 1, 0);
					v[1] = adult(h, HOUSE1, 3, 0);
					home(h, v[0], bedHead(HOUSE1, 0));
					home(h, v[1], outFoot.south());
					housingNow(h);
					h.assertTrue(housed(h, v[0]) && !housed(h, v[1]), "one adult is housed, the other sleeps outside");
				});
		seq = toDawn(h, seq).thenExecute(() -> {
			h.assertTrue(GrowthStats.lastDawn().reason() == Reason.TOO_FEW_READY, "an unhoused adult is not ready: " + GrowthStats.lastDawn());
			h.assertTrue(licence(h).filter(l -> l.active()).isEmpty(), "no licence");
		});
		seq.thenIdle(1500).thenExecute(() -> {
			h.assertValueEqual(0, loadedBabies(), "no baby");
			end(h);
		});
	}

	/** Not enough food: 11 bread are 55 nutrition for 20 a day, 2.75 days. */
	@GameTest(environment = "villageheart-gametest:growth_low_food", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void lowFoodNoBaby(GameTestHelper h) {
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, 4, 0, 11).thenExecute(() -> {
			h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 5000);
			v[0] = adult(h, HOUSE1, 1, 0);
			v[1] = adult(h, HOUSE1, 3, 0);
			home(h, v[0], bedHead(HOUSE1, 0));
			home(h, v[1], bedHead(HOUSE1, 1));
			housingNow(h);
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			GrowthStats.Dawn d = GrowthStats.lastDawn();
			h.assertTrue(d.reason() == Reason.LOW_FOOD, "2.75 food days are too few: " + d);
		});
		seq.thenIdle(1500).thenExecute(() -> {
			h.assertValueEqual(0, loadedBabies(), "no baby");
			end(h);
		});
	}

	// ---- T-L2-7 ---------------------------------------------------------------------------------

	/** The gate: in the claim, nobody breeds without a licence, even with food, a free bed and a seeded target. */
	@GameTest(environment = "villageheart-gametest:growth_gate", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void unlicensedPairCannotBreedInClaim(GameTestHelper h) {
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 100);
			for (int i = 0; i < 2; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * i, 0);
				breadInPockets(v[i], 3);
			}
			h.assertFalse(v[0].canBreed() || v[1].canBreed(), "in the claim: no licence, no breeding");
			h.assertTrue(dev.villageheart.growth.BreedingGate.blocks(v[0]), "the gate blocks");
		});
		// seed both by hand every 20 ticks for 400 ticks, as the worst case of vanilla's own seeding
		for (int i = 0; i < 20; i++) {
			seq = seq.thenExecuteAfter(20, () -> {
				for (int k = 0; k < 2; k++) v[k].getBrain().setMemory(MemoryModuleType.BREED_TARGET, v[1 - k]);
			});
		}
		seq.thenExecute(() -> {
			h.assertValueEqual(0, loadedBabies(), "no baby appeared");
			h.assertValueEqual(0, GrowthStats.unlicensedBirths(), "no unlicensed birth");
			end(h);
		});
	}
}
