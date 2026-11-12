package dev.villageheart.gametest;

import static dev.villageheart.gametest.GrowthTestSupport.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.growth.License;
import dev.villageheart.growth.BreedingGate;
import dev.villageheart.growth.Growth;
import dev.villageheart.growth.GrowthStats;
import dev.villageheart.core.growth.LicenceState;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.villager.Villager;

/** M23 review round 1 (docs/reviews/M23-r1.md): items 1 to 3. Rules: see {@link GrowthTestSupport}. */
public class GrowthReviewGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/**
	 * Item 1: a vanilla birth far outside the claim is legal vanilla breeding: it neither counts as unlicensed nor logs an error. A birth
	 * inside the claim without a licence still does (the alarm keeps working).
	 */
	@GameTest(environment = "villageheart-gametest:growth_review_outside_birth", structure = YARD, skyAccess = true, maxTicks = 400)
	public void vanillaBirthOutsideClaimIsNotUnlicensed(GameTestHelper h) {
		scene(h, 2, 0, 0).thenExecute(() -> {
			var level = h.getLevel();
			HeartLocation hl = village(h).heart();
			BlockPos farBed = new BlockPos(hl.x() + 200, hl.y(), hl.z());
			Villager far = EntityType.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			far.snapTo(farBed.getX() + 0.5, farBed.getY(), farBed.getZ() + 0.5, 0.0F, 0.0F);
			h.assertFalse(BreedingGate.claimContains(level.dimension(), farBed), "(sanity) the far bed is outside the claim");
			Growth.onBirth(level, far, farBed);
			h.assertValueEqual(0, GrowthStats.unlicensedBirths(), "a vanilla birth outside the claim is not an unlicensed birth");

			BlockPos nearBed = BuildingTestSupport.inside(HOUSE1, 1, 0, 1).offset(0, 0, 0);
			Villager near = EntityType.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			BlockPos nearAbs = h.absolutePos(nearBed);
			near.snapTo(nearAbs.getX() + 0.5, nearAbs.getY(), nearAbs.getZ() + 0.5, 0.0F, 0.0F);
			Growth.onBirth(level, near, nearAbs);
			h.assertValueEqual(1, GrowthStats.unlicensedBirths(), "a birth inside the claim without a licence still counts");
			end(h);
		});
	}

	/** Item 2: after a licensed birth the licence is BORN and exempts nobody for the rest of the day (one baby a day). */
	@GameTest(environment = "villageheart-gametest:growth_review_born_gate", structure = YARD, skyAccess = true, maxTicks = 600)
	public void bornLicenceStopsExemptingParents(GameTestHelper h) {
		Villager[] v = new Villager[2];
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 5000);
			for (int i = 0; i < 2; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * i, 0);
				v[i].setNoAi(true);
				breadInPockets(v[i], 3);
			}
			license(h, v[0], v[1]);
		}).thenIdle(3).thenExecute(() -> {
			h.assertTrue(BreedingGate.licenceOf(v[0]).isPresent() && BreedingGate.licenceOf(v[1]).isPresent(), "licensed parents (sanity)");
			h.assertFalse(BreedingGate.blocks(v[0]) || BreedingGate.blocks(v[1]), "an active licence exempts its parents");
			h.assertTrue(v[0].canBreed() && v[1].canBreed(), "and they can breed (sanity)");
			License l = licence(h).orElseThrow();
			Growth.forceLicenceForTest(h.getLevel().getServer(), l.seeded(l.bed().getAsLong(), 5, 5).born(UUID.randomUUID()));
		}).thenIdle(3).thenExecute(() -> {
			for (Villager x : v) {
				x.setAge(0);
				breadInPockets(x, 3);
			}
			h.assertFalse(licence(h).orElseThrow().active(), "the licence is BORN");
			for (Villager x : v) {
				h.assertTrue(BreedingGate.blocks(x), "the gate blocks a parent of a BORN licence");
				h.assertFalse(BreedingGate.licenceOf(x).isPresent(), "a BORN licence is nobody's licence");
				h.assertFalse(x.canBreed(), "a parent of a BORN licence cannot breed again today, food and age notwithstanding");
			}
			end(h);
		});
	}

	/**
	 * Item 3: a licensed parent that is not the SEEDED initiator must drop every bed vanilla takes for it, wherever it is; the seeded
	 * initiator keeps the reserved bed path; an unlicensed villager outside the claim is left alone.
	 */
	@GameTest(environment = "villageheart-gametest:growth_review_bed_drop", structure = YARD, skyAccess = true, maxTicks = 600)
	public void licensedNonInitiatorDropsOutsideBed(GameTestHelper h) {
		Villager[] v = new Villager[3];
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 5000);
			for (int i = 0; i < 3; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * (i % 2), i / 2 * 2);
				v[i].setNoAi(true);
				breadInPockets(v[i], 3);
			}
			license(h, v[0], v[1]);
			// the pair is ordered by UUID: the initiator (a) is whichever of the two the licence names first
			if (!licence(h).orElseThrow().pair().a().equals(v[0].getUUID())) {
				Villager t = v[0];
				v[0] = v[1];
				v[1] = t;
			}
		}).thenIdle(3).thenExecute(() -> {
			var level = h.getLevel();
			HeartLocation hl = village(h).heart();
			BlockPos outside = new BlockPos(hl.x() + 88, hl.y(), hl.z());
			BlockPos inside = h.absolutePos(bedHead(HOUSE1, 3));
			h.assertFalse(BreedingGate.claimContains(level.dimension(), outside), "(sanity) outside");
			h.assertTrue(BreedingGate.claimContains(level.dimension(), inside), "(sanity) inside");
			h.assertTrue(BreedingGate.licenceOf(v[1]).isPresent() && Growth.seededLicenceOf(v[1]).isEmpty(), "(sanity) b is licensed, not the seeded initiator");

			h.assertTrue(Growth.mustDropBed(level, v[1], outside), "licensed non-initiator b: an outside bed goes back");
			h.assertTrue(Growth.mustDropBed(level, v[0], outside), "licensed initiator a before seeding: an outside bed goes back");
			h.assertFalse(Growth.mustDropBed(level, v[2], outside), "unlicensed villager outside the claim: vanilla applies");
			h.assertTrue(Growth.mustDropBed(level, v[2], inside), "unlicensed villager, claim bed: goes back (as before)");

			License l = licence(h).orElseThrow();
			// same tick, no courtship step in between: the gate snapshot is refreshed by hand
			Growth.forceLicenceForTest(level.getServer(), l.seeded(l.bed().getAsLong(), 5, 5));
			Growth.refreshGate(level.getServer());
			h.assertTrue(Growth.seededLicenceOf(v[0]).isPresent(), "(sanity) a is the seeded initiator: " + describe(h) + " gate " + BreedingGate.licenceOf(v[0]));
			h.assertFalse(Growth.mustDropBed(level, v[0], outside), "the seeded initiator is served by bedForBirth, not dropped here");
			h.assertTrue(Growth.mustDropBed(level, v[1], outside), "b still drops an outside bed while a is seeded");
			end(h);
		});
	}

	/**
	 * Item (r2) 2: a licensed pair standing within the birth distance, whose cached sight snapshot says the partner is unseen for the whole
	 * run (the inverse of the {@code nearby()} helper: an empty snapshot every tick once the licence is SEEDED), still has its baby, with
	 * its first seed and no abort. Without the fresh sight test every run stops at once as NOT_VISIBLE, and three seeds lapse the licence.
	 */
	@GameTest(environment = "villageheart-gametest:growth_review_sight_gap", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void sightGapWithinBirthDistanceDoesNotCostASeed(GameTestHelper h) {
		Villager[] v = new Villager[2];
		h.onEachTick(() -> {
			if (v[0] == null) return;
			var l = licence(h);
			if (l.isPresent() && l.get().state() == LicenceState.SEEDED) {
				for (Villager x : v) x.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, NearestVisibleLivingEntities.empty());
			}
		});
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(WageTestSupport.dayIndex(h) * 24000L + 100);
			for (int i = 0; i < 2; i++) {
				v[i] = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22));
				v[i].setAge(0);
				breadInPockets(v[i], 3);
			}
			placeAt(h, v[0], 22.2, 0, 22.5);
			placeAt(h, v[1], 23.4, 0, 22.5);
			license(h, v[0], v[1]);
		}).thenWaitUntil(() -> h.assertTrue(!babies(h).isEmpty(), "no baby yet: " + describe(h))).thenExecute(() -> {
			License l = licence(h).orElseThrow();
			h.assertTrue(l.state() == LicenceState.BORN, "BORN: " + describe(h));
			h.assertValueEqual(1, l.seeds(), "the first seed was enough: " + describe(h));
			h.assertValueEqual(0, GrowthStats.aborts(), "no aborted run: " + describe(h));
			h.assertTrue(GrowthStats.sightRescues() > 0, "the fresh sight test was what kept the run alive: " + describe(h));
			end(h);
		});
	}
}
