package dev.villageheart.gametest;

import static dev.villageheart.gametest.GrowthTestSupport.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.growth.BreedingPair;
import dev.villageheart.core.growth.LapseReason;
import dev.villageheart.core.growth.LicenceState;
import dev.villageheart.core.growth.License;
import dev.villageheart.core.growth.Reason;
import dev.villageheart.growth.BreedTrace;
import dev.villageheart.growth.BreedingGate;
import dev.villageheart.growth.Growth;
import dev.villageheart.growth.GrowthStats;
import dev.villageheart.mixin.VillagerFoodAccess;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * Layer 2: the courtship, the baby's bed, the gate and the lifecycle of the licence (M23). Rules: see {@link GrowthTestSupport}; one environment per
 * test.
 */
public class GrowthCourtshipGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	private static final BlockPos YARD_BED = new BlockPos(24, 0, 24); // in the open, outside any House

	private static long dayStart(GameTestHelper h) { return WageTestSupport.dayIndex(h) * 24000L; }

	private static void assertNoBaby(GameTestHelper h, String when) {
		h.assertTrue(babies(h).isEmpty(), "a baby appeared " + when + ": " + describe(h));
		h.assertValueEqual(0, loadedBabies(), "babies seen by the entity-load listener " + when);
		h.assertValueEqual(0, GrowthStats.births(), "births counted by the hook " + when);
	}

	// ---- T-L2-2 ---------------------------------------------------------------------------------

	/** Roadmap L2: no free House bed, no baby in three days, although vanilla alone had food and a free bed (outside any House). */
	@GameTest(environment = "villageheart-gametest:growth_no_free_bed", structure = YARD, skyAccess = true, maxTicks = 17500)
	public void noFreeBedNoBabyInThreeDays(GameTestHelper h) {
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, 2, 0, 32).thenExecute(() -> WorkerTestSupport.bed(h, YARD_BED))
				.thenWaitUntil(() -> h.assertTrue(WorkerTestSupport.poiPresent(h, YARD_BED.south()), "the bed outside the House has a POI"))
				.thenExecute(() -> {
					h.getLevel().setDayTime(dayStart(h) + 5000);
					// the adults live in the House but stand in the open next to the free bed, where vanilla's make-love would find it
					v[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22));
					v[1] = WorkerTestSupport.spawnVillager(h, new BlockPos(23, 0, 22));
					for (Villager x : v) x.setAge(0);
					home(h, v[0], bedHead(HOUSE1, 0));
					home(h, v[1], bedHead(HOUSE1, 1));
					for (Villager x : v) breadInPockets(x, 3); // vanilla's canBreed would be true
					housingNow(h);
					h.assertTrue(WorkerTestSupport.poiHasSpace(h, YARD_BED.south()), "the yard bed is free");
				});
		for (int day = 1; day <= 3; day++) {
			int d = day;
			seq = toDawn(h, seq).thenExecute(() -> {
				h.assertTrue(GrowthStats.lastDawn().reason() == Reason.NO_FREE_BED, "day " + d + ": " + GrowthStats.lastDawn());
				h.assertTrue(licence(h).filter(l -> l.active()).isEmpty(), "no licence on day " + d);
			});
			// days 1 and 2: the whole courtship window of these villagers; day 3: to the end of every IDLE window
			// the adults are also seeded by hand all day, the worst case of vanilla's own seeding: without the gate (and the free-bed rule) a baby follows
			seq = watch(seq, d < 3 ? 1500 : 12000, 20, () -> {
				assertNoBaby(h, "on day " + d);
				for (int k = 0; k < 2; k++) if (!v[k].getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)) v[k].getBrain().setMemory(MemoryModuleType.BREED_TARGET, v[1 - k]);
			});
		}
		seq.thenExecute(() -> {
			log("T-L2-2: " + BreedTrace.summary() + " unlicensed " + GrowthStats.unlicensedBirths());
			h.assertTrue(WorkerTestSupport.poiHasSpace(h, YARD_BED.south()), "the yard bed was never taken");
			h.assertValueEqual(0, GrowthStats.unlicensedBirths(), "no unlicensed birth");
			end(h);
		});
	}

	// ---- T-L2-3 ---------------------------------------------------------------------------------

	/** One licence a day: of four ready adults only the licensed pair breeds; the next dawn licenses the other pair. */
	@GameTest(environment = "villageheart-gametest:growth_one_birth_per_day", structure = YARD, skyAccess = true, maxTicks = 6500)
	public void oneBirthPerDay(GameTestHelper h) {
		Villager[] v = new Villager[4];
		BreedingPair[] first = new BreedingPair[1];
		GameTestSequence seq = scene(h, 4, 2, 64).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 5000);
			for (int i = 0; i < 4; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * (i % 2), i / 2 * 2);
				home(h, v[i], bedHead(HOUSE1, i));
				breadInPockets(v[i], 3);
			}
			housingNow(h);
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			h.assertTrue(GrowthStats.lastDawn().reason() == Reason.LICENSED, "day 1: " + GrowthStats.lastDawn());
			first[0] = licence(h).orElseThrow().pair();
		});
		seq = watch(seq, 1500, 20, () -> {
			for (Villager x : v) {
				if (!first[0].contains(x.getUUID())) h.assertFalse(x.canBreed(), "an unlicensed adult can breed: " + describe(h));
			}
			h.assertTrue(loadedBabies() <= 1, "more than one baby on day 1: " + describe(h));
		});
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1, loadedBabies(), "exactly one baby on day 1: " + describe(h));
			h.assertValueEqual(1, GrowthStats.births(), "the hook counted one");
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			h.assertTrue(GrowthStats.lastDawn().reason() == Reason.LICENSED, "day 2: " + GrowthStats.lastDawn());
			BreedingPair second = licence(h).orElseThrow().pair();
			h.assertTrue(!second.contains(first[0].a()) && !second.contains(first[0].b()), "the parents of yesterday are on cooldown: " + second);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1500, "the second baby", () -> h.assertTrue(loadedBabies() >= 2, "no second baby yet: " + describe(h)));
		seq.thenExecute(() -> {
			h.assertValueEqual(2, loadedBabies(), "two babies after two days");
			h.assertValueEqual(0, GrowthStats.unlicensedBirths(), "no unlicensed birth");
			end(h);
		});
	}

	// ---- T-L2-5 / 5b ----------------------------------------------------------------------------

	private static void nearby(GameTestHelper h, Villager a, Villager b) {
		var level = h.getLevel();
		a.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, a, List.<LivingEntity>of(b)));
		b.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, b, List.<LivingEntity>of(a)));
	}

	private static void assertTracks(GameTestHelper h, Villager v, Villager other, String what) {
		WalkTarget wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
		h.assertTrue(wt != null, what + ": a walk target exists");
		h.assertTrue(wt.getTarget() instanceof EntityTracker t && t.getEntity() == other, what + ": it follows the partner");
		h.assertValueEqual(1, wt.getCloseEnoughDist(), what + ": close-enough distance");
	}

	/**
	 * The root cause of vanilla's breeding abort (plan 3.5), deterministic: a pair that "reached" each other at block offset 2 stands 2.6
	 * blocks apart; vanilla's birth tick needs sqrt(5) and leaves them parked. For a licensed pair the walk uses close-enough 1 and the
	 * behaviour walks them together again.
	 */
	@GameTest(environment = "villageheart-gametest:growth_parked_gap", structure = YARD, skyAccess = true, maxTicks = 600)
	public void makeLoveClosesParkedGap(GameTestHelper h) {
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 15000); // night: the courtship itself stays out of the way
			Villager x = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22)), y = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22));
			Villager a = x.getUUID().compareTo(y.getUUID()) < 0 ? x : y, b = a == x ? y : x;
			for (Villager v : new Villager[] {a, b}) {
				v.setAge(0);
				v.setNoAi(true);
				breadInPockets(v, 3);
			}
			license(h, a, b);
			placeAt(h, a, 22.2, 0, 22.5);
			placeAt(h, b, 24.8, 0, 22.5); // blocks 22 and 24 (Manhattan 2), 2.6 apart
			nearby(h, a, b);
			a.getBrain().setMemory(MemoryModuleType.BREED_TARGET, b);
			h.assertTrue(a.distanceToSqr(b) > 5.0 && a.distanceToSqr(b) < 9.0, "the pair is parked in the gap: " + a.distanceTo(b));
			var ml = new VillagerMakeLove();
			long t = h.getLevel().getGameTime();
			h.assertTrue(ml.tryStart(h.getLevel(), a, t), "the behaviour starts");
			assertTracks(h, a, b, "start (fix a), initiator");
			assertTracks(h, b, a, "start (fix a), partner");
			a.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			b.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			ml.tickOrStop(h.getLevel(), a, t + 1);
			assertTracks(h, a, b, "tick in the gap (fix b), initiator");
			assertTracks(h, b, a, "tick in the gap (fix b), partner");
			ml.doStop(h.getLevel(), a, t + 2);
			end(h);
		});
	}

	/** The same pair with live brains still breeds (the probabilistic twin of the test above). */
	@GameTest(environment = "villageheart-gametest:growth_parked_breeds", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void parkedPairStillBreeds(GameTestHelper h) {
		Villager[] v = new Villager[2];
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 100);
			v[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22));
			v[1] = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22));
			for (Villager x : v) {
				x.setAge(0);
				breadInPockets(x, 3);
			}
			placeAt(h, v[0], 22.2, 0, 22.5);
			placeAt(h, v[1], 24.8, 0, 22.5);
			license(h, v[0], v[1]);
		}).thenWaitUntil(() -> h.assertTrue(!babies(h).isEmpty(), "no baby yet: " + describe(h)))
				.thenExecute(() -> {
					h.assertTrue(licence(h).orElseThrow().state() == LicenceState.BORN, "BORN: " + describe(h));
					log("T-L2-5b: aborts " + GrowthStats.aborts() + " " + BreedTrace.summary());
					end(h);
				});
	}

	// ---- T-L2-8 ---------------------------------------------------------------------------------

	/** The licence and the held bed survive a save and a load. */
	@GameTest(environment = "villageheart-gametest:growth_reload", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void licenceSurvivesSavedDataReload(GameTestHelper h) {
		Villager[] v = new Villager[3];
		License[] before = new License[1];
		GameTestSequence seq = scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 5000);
			for (int i = 0; i < 2; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * i, 0);
				home(h, v[i], bedHead(HOUSE1, i));
			}
			v[2] = adult(h, HOUSE1, 2, 3);
			housingNow(h);
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			before[0] = licence(h).orElseThrow(() -> h.assertionException("licensed: " + describe(h)));
			// the third adult may have found a House bed of its own and be a parent: the licence says who is
			java.util.List<Villager> all = new java.util.ArrayList<>(java.util.List.of(v));
			Villager[] sorted = new Villager[3];
			int n = 0;
			for (Villager x : all) if (before[0].pair().contains(x.getUUID())) sorted[n++] = x;
			for (Villager x : all) if (!before[0].pair().contains(x.getUUID())) sorted[n++] = x;
			System.arraycopy(sorted, 0, v, 0, 3); // v[0], v[1] are the parents now, v[2] is the other
			v[0].setNoAi(true); // frozen after the dawn (the census leaves frozen villagers out): nothing advances
			v[1].setNoAi(true);
			h.assertTrue(before[0].bed().isPresent() && before[0].state() == LicenceState.WAITING, "a fresh licence: " + before[0]);
			dev.villageheart.world.VillageSavedData back;
			try {
				back = VillageTestSupport.reload(h.getLevel().getServer());
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			h.assertTrue(back.growth().licence().equals(java.util.Optional.of(before[0])), "the licence read back from disk is the same: " + back.growth());
			h.assertValueEqual(before[0].bed(), back.growth().licence().get().bed(), "the bed");
			BreedingGate.reset(); // a restart: the gate is rebuilt from the saved state
		}).thenIdle(2).thenExecute(() -> {
			h.assertTrue(BreedingGate.licenceOf(v[0]).isPresent() && BreedingGate.licenceOf(v[1]).isPresent(), "the gate exempts exactly the pair after the reload");
			h.assertTrue(BreedingGate.licenceOf(v[2]).isEmpty(), "and nobody else");
			h.assertFalse(spaceAbs(h, BlockPos.of(before[0].bed().getAsLong())), "the bed is still held");
			end(h);
		});
	}

	/** After a reload the behaviour state is gone: a SEEDED licence seeds its initiator again without counting a seed. */
	@GameTest(environment = "villageheart-gametest:growth_resume", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void seededLicenceResumesAfterBrainReset(GameTestHelper h) {
		Villager[] v = new Villager[2];
		License[] seeded = new License[1];
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 100);
			v[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(22, 0, 22));
			v[1] = WorkerTestSupport.spawnVillager(h, new BlockPos(24, 0, 22));
			for (Villager x : v) x.setAge(0);
			License l = license(h, v[0], v[1]);
			// as if the pair was seeded before a reload: the licence says SEEDED, nobody has a BREED_TARGET
			seeded[0] = l.seeded(l.bed().getAsLong(), 0, 0);
			Growth.forceLicenceForTest(h.getLevel().getServer(), seeded[0]);
			h.assertFalse(v[0].getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET) || v[1].getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET),
					"nobody has a target");
		}).thenWaitUntil(() -> h.assertTrue(!babies(h).isEmpty(), "no baby yet: " + describe(h)))
				.thenExecute(() -> {
					License l = licence(h).orElseThrow();
					h.assertTrue(l.state() == LicenceState.BORN, "BORN: " + describe(h));
					// every seed after the first follows an abort of an earlier run; the re-seed after the "reload" itself counts none
					h.assertValueEqual(l.aborts() + 1, l.seeds(), "the re-seed did not count a seed: " + describe(h));
					end(h);
				});
	}

	// ---- T-L2-10 --------------------------------------------------------------------------------

	/** Breaking the Heart gives the reserved bed back and leaves nobody breedable. */
	@GameTest(environment = "villageheart-gametest:growth_release", structure = YARD, skyAccess = true, maxTicks = 600)
	public void releaseFreesReservedBed(GameTestHelper h) {
		Villager[] v = new Villager[2];
		BlockPos[] bed = new BlockPos[1];
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 15000);
			for (int i = 0; i < 2; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * i, 0);
				v[i].setNoAi(true);
				((VillagerFoodAccess) v[i]).villageheart$setFoodLevel(3);
			}
			License l = license(h, v[0], v[1]);
			bed[0] = BlockPos.of(l.bed().getAsLong());
			h.assertFalse(spaceAbs(h, bed[0]), "the licence holds the bed");
			Villager init = v[0].getUUID().equals(l.pair().a()) ? v[0] : v[1], other = init == v[0] ? v[1] : v[0];
			for (Villager x : v) Growth.topUpFood(x);
			Growth.forceLicenceForTest(h.getLevel().getServer(), l.seeded(l.bed().getAsLong(), 3, 3));
			init.getBrain().setMemory(MemoryModuleType.BREED_TARGET, other);
			h.assertTrue(BuildingTestSupport.owner().gameMode.destroyBlock(h.absolutePos(new BlockPos(3, 1, 1))), "the Owner broke the Heart");
			h.assertTrue(spaceAbs(h, bed[0]), "the bed has a free ticket again");
			h.assertFalse(BreedingGate.active(), "the gate is off");
			for (Villager x : v) {
				h.assertFalse(x.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET), "no breed target");
				h.assertValueEqual(3, ((VillagerFoodAccess) x).villageheart$getFoodLevel(), "the food level is what it was");
			}
			end(h);
		});
	}

	// ---- T-L2-11 --------------------------------------------------------------------------------

	/** A pair that cannot reach each other lapses CANNOT_MEET and is skipped for the next days; growth is not blocked for ever. */
	@GameTest(environment = "villageheart-gametest:growth_cannot_meet", structure = YARD, skyAccess = true, maxTicks = 4500)
	public void cannotMeetLapsesAndIsSkipped(GameTestHelper h) {
		BlockPos house2 = new BlockPos(15, 0, 14); // right next to House 1: A and B stand 3.0 apart, two walls between them
		Villager[] v = new Villager[3]; // A, B, C
		BreedingPair[] ab = new BreedingPair[1];
		GameTestSequence seq = scene(h, 4, 0, 32).thenExecute(() -> {
			buildHouse(h, house2, 4);
		}).thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, marker(house2), dev.villageheart.block.MarkerStatus.VALID);
			for (int i = 0; i < 4; i++) h.assertTrue(WorkerTestSupport.poiPresent(h, bedHead(house2, i)), "House 2 bed " + i + " has a POI");
		}).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 5000);
			v[0] = adult(h, HOUSE1, 4, 3); // A: House 1, bed 3
			v[1] = adult(h, house2, 0, 3); // B: House 2, bed 2
			v[2] = adult(h, HOUSE1, 0, 0); // C: House 1, bed 0
			home(h, v[0], bedHead(HOUSE1, 3));
			home(h, v[1], bedHead(house2, 2));
			home(h, v[2], bedHead(HOUSE1, 0));
			placeAt(h, v[0], 13.5, 1, 18.5); // A: east end of House 1
			placeAt(h, v[1], 16.5, 1, 18.5); // B: west end of House 2, 3.0 from A through two walls
			placeAt(h, v[2], 9.5, 1, 16.5); // C: west end of House 1, 4.5 from A
			ab[0] = BreedingPair.of(v[0].getUUID(), v[1].getUUID());
			// seal both doors from outside: nobody can leave either room
			for (BlockPos o : new BlockPos[] {HOUSE1, house2}) {
				int doorX = o.getX() + 3;
				h.setBlock(new BlockPos(doorX, 1, o.getZ() - 1), Blocks.OAK_PLANKS);
				h.setBlock(new BlockPos(doorX, 2, o.getZ() - 1), Blocks.OAK_PLANKS);
			}
			housingNow(h);
			h.assertTrue(housed(h, v[0]) && housed(h, v[1]) && housed(h, v[2]), "all three are housed");
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			h.assertTrue(GrowthStats.lastDawn().reason() == Reason.LICENSED, "dawn 1: " + GrowthStats.lastDawn());
			h.assertTrue(licence(h).orElseThrow().pair().equals(ab[0]), "dawn 1 licenses the closest pair, A and B");
			h.assertValueEqual(0, licence(h).orElseThrow().seeds(), "they never get close enough to be seeded");
		});
		seq = seq.thenWaitUntil(() -> {
			License l = licence(h).orElseThrow();
			h.assertTrue(l.state() == LicenceState.LAPSED && l.lapse().orElseThrow() == LapseReason.CANNOT_MEET, "the licence lapsed CANNOT_MEET: " + describe(h));
		}).thenExecute(() -> {
			h.assertValueEqual(0, licence(h).orElseThrow().seeds(), "never seeded");
			h.assertTrue(Growth.state(h.getLevel().getServer()).skipPairs().contains(ab[0]), "the pair is remembered as failed");
			h.assertTrue(spaceAbs(h, BlockPos.of(licence(h).orElseThrow().bed().getAsLong())), "its bed was released");
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			h.assertTrue(GrowthStats.lastDawn().reason() == Reason.LICENSED, "dawn 2: " + GrowthStats.lastDawn());
			h.assertTrue(!licence(h).orElseThrow().pair().equals(ab[0]), "dawn 2 skips A and B");
		});
		seq = toDawn(h, seq).thenExecute(() -> {
			h.assertTrue(licence(h).filter(l -> l.pair().equals(ab[0])).isEmpty(), "dawn 3 does not license A and B either: " + describe(h));
			end(h);
		});
	}

	// ---- T-L2-12 --------------------------------------------------------------------------------

	/**
	 * Vanilla breeding just outside the claim must not fill the village's beds: the only free bed within 48 blocks of the pair lies inside the
	 * claim, and the make-love hook gives it back.
	 *
	 * <p>The pair stands on the heart's own Y, 88 blocks east of it (the sphere is 80; the bed is at 76, twelve blocks away, inside a villager's
	 * path range). An earlier setup used the world ground, which is a few blocks below the heart, so horizontal 82 was only about 2 blocks outside
	 * the sphere: in the full suite a stroll walked them in, {@code canBreed} became false, and no make-love run ever started
	 * ({@code births=0 aborts={}}). The leash keeps that stroll from crossing the sphere, and every other free bed in range is removed so a
	 * neighbour test's bed cannot be the one vanilla takes.
	 */
	@GameTest(environment = "villageheart-gametest:growth_outside_pair", structure = YARD, skyAccess = true, maxTicks = 4500)
	public void outsidePairCannotTakeClaimBed(GameTestHelper h) {
		Villager[] v = new Villager[2];
		BlockPos[] bedHead = new BlockPos[1];
		int[] chunks = new int[4];
		boolean[] watch = {false};
		long[] finishedAt = {-1};
		// registered from the test body (never from a callback): holds the pair outside the claim and seeds one make-love run
		h.onEachTick(() -> {
			if (!watch[0] || v[0] == null) return;
			// hold them until the assertions: a stroll after the run is still up to 10 blocks, enough to step inside from dx 88
			leashOutside(h, v);
			if (BreedTrace.finished(v[0].getUUID())) {
				if (finishedAt[0] < 0) finishedAt[0] = h.getTick();
				return;
			}
			nearby(h, v[0], v[1]);
			if (!v[0].getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET) && v[0].canBreed() && v[1].canBreed()) {
				v[0].getBrain().setMemory(MemoryModuleType.BREED_TARGET, v[1]);
			}
		});
		GameTestSequence seq = scene(h, 2, 0, 32).thenExecute(() -> {
			HeartLocation hl = village(h).heart();
			bedHead[0] = new BlockPos(hl.x() + 76, hl.y(), hl.z() + 1); // foot is one north; 76 < 80, inside the sphere
			chunks[0] = (hl.x() + 64) >> 4;
			chunks[1] = (hl.x() + 100) >> 4;
			chunks[2] = (hl.z() - 16) >> 4;
			chunks[3] = (hl.z() + 16) >> 4;
			for (int cx = chunks[0]; cx <= chunks[1]; cx++) for (int cz = chunks[2]; cz <= chunks[3]; cz++) h.getLevel().setChunkForced(cx, cz, true);
		}).thenWaitUntil(() -> {
			BlockPos spot = new BlockPos(village(h).heart().x() + 88, village(h).heart().y(), village(h).heart().z());
			h.assertTrue(h.getLevel().isPositionEntityTicking(spot) && h.getLevel().isPositionEntityTicking(bedHead[0]),
					"the pad and the bed are entity-ticking (a forced chunk): pad " + h.getLevel().isPositionEntityTicking(spot) + " bed "
							+ h.getLevel().isPositionEntityTicking(bedHead[0]));
		}).thenExecute(() -> {
			HeartLocation hl = village(h).heart();
			carvePad(h, hl);
			net.minecraft.world.level.block.state.BlockState bedState = Blocks.RED_BED.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
			BlockPos claimFoot = bedHead[0].north();
			setBed(h, claimFoot, bedState);
			// home beds beside the pair, same X, so walking home cannot enter the claim
			BlockPos far = new BlockPos(hl.x() + 88, hl.y(), hl.z());
			setBed(h, far.north(6), bedState);
			setBed(h, far.north(8), bedState);
			clearStrangers(h, far); // before the pair exists: the box must not discard them
		}).thenWaitUntil(() -> h.assertTrue(h.getLevel().getPoiManager().getType(bedHead[0]).isPresent(), "the claim's bed has a POI"))
				.thenExecute(() -> {
					HeartLocation hl = village(h).heart();
					BlockPos far = new BlockPos(hl.x() + 88, hl.y(), hl.z());
					for (int i = 0; i < 2; i++) {
						v[i] = WorkerTestSupport.spawnVillager(h, new BlockPos(2, 0, 2));
						v[i].snapTo(far.getX() + 0.3 + i * 1.2, far.getY(), far.getZ() + 0.5, i == 0 ? 90.0F : -90.0F, 0.0F);
						v[i].setAge(0);
						breadInPockets(v[i], 3);
					}
					claimBedAbs(h, v[0], far.north(6).south());
					claimBedAbs(h, v[1], far.north(8).south());
					h.getLevel().setDayTime(dayStart(h) + 100);
				});
		seq = seq.thenWaitUntil(() -> {
			clearForeignBeds(h, bedHead[0]);
			long free = freeHomeBeds(h, bedHead[0]);
			h.assertValueEqual(1L, free, "the claim bed is the only free bed within 48 blocks");
		}).thenExecute(() -> {
			long phase = Math.floorMod(h.getLevel().getDayTime(), 24000L);
			h.assertTrue(phase >= 10 && phase < 500, "the pair is in the morning idle window: phase " + phase);
			h.assertFalse(BreedingGate.blocks(v[0]) || BreedingGate.blocks(v[1]), "the pair is outside the claim: vanilla applies");
			h.assertTrue(BreedingGate.claimContains(h.getLevel().dimension(), bedHead[0]), "the bed is inside the claim");
			h.assertTrue(v[0].distanceToSqr(v[1]) < 5.0, "the pair starts inside the birth distance: " + v[0].distanceTo(v[1]));
			h.assertTrue(v[0].canBreed() && v[1].canBreed(), "vanilla canBreed is true out here");
			watch[0] = true;
		});
		// one make-love run is 275-324 ticks; 800 covers the start plus that run
		seq.thenIdle(800).thenExecute(() -> {
			watch[0] = false;
			var path = v[0].getNavigation().createPath(bedHead[0], 1);
			h.assertTrue(BreedTrace.finished(v[0].getUUID()), "the initiator's make-love run ended: " + outsideDiag(h, v, bedHead[0], path));
			h.assertTrue(GrowthStats.droppedBeds() >= 1, "vanilla's run took the claim's bed and the hook gave it back: " + outsideDiag(h, v, bedHead[0], path));
			h.assertTrue(BreedTrace.aborts(BreedTrace.Abort.NO_BED, false) >= 1, "and the run ended as a NO_BED abort: " + outsideDiag(h, v, bedHead[0], path));
			h.assertFalse(BreedingGate.blocks(v[0]) || BreedingGate.blocks(v[1]), "the pair is still outside the claim: " + v[0].blockPosition());
			h.assertTrue(spaceAbs(h, bedHead[0]), "the claim's bed still has a free ticket");
			h.assertTrue(babies(h).isEmpty(), "no baby in the test area");
			h.assertValueEqual(0, GrowthStats.unlicensedBirths(), "no unlicensed birth");
			log("T-L2-12: run finished at test tick " + finishedAt[0] + " " + BreedTrace.summary());
			for (Villager x : v) x.discard();
			for (int cx = chunks[0]; cx <= chunks[1]; cx++) for (int cz = chunks[2]; cz <= chunks[3]; cz++) h.getLevel().setChunkForced(cx, cz, false);
			end(h);
		});
	}

	/** Puts the pair back on its pad (8 blocks outside the sphere; a stroll is up to 10) and drops mobs that would panic them. */
	private static void leashOutside(GameTestHelper h, Villager[] v) {
		HeartLocation hl = village(h).heart();
		for (int i = 0; i < 2; i++) {
			Villager x = v[i];
			if (x.isSleeping()) x.stopSleeping();
			x.snapTo(hl.x() + 88.3 + i * 1.2, hl.y(), hl.z() + 0.5, i == 0 ? 90.0F : -90.0F, 0.0F);
		}
		var level = h.getLevel();
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(v[0].blockPosition()).inflate(24);
		level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box, e -> true).forEach(net.minecraft.world.entity.Entity::discard);
	}

	/** Stone floor and air from the claim bed out to the pad, so a neighbour test's leftover blocks cannot block the path or the line of sight. */
	private static void carvePad(GameTestHelper h, HeartLocation hl) {
		var level = h.getLevel();
		for (int x = hl.x() + 74; x <= hl.x() + 92; x++) for (int z = hl.z() - 10; z <= hl.z() + 4; z++) {
			level.setBlock(new BlockPos(x, hl.y() - 1, z), Blocks.STONE.defaultBlockState(), 3);
			for (int y = 0; y <= 2; y++) level.setBlock(new BlockPos(x, hl.y() + y, z), Blocks.AIR.defaultBlockState(), 3);
		}
	}

	/** Villagers and bells left by earlier tests in this reused world, within the pair's breeding range. */
	private static void clearStrangers(GameTestHelper h, BlockPos pad) {
		var level = h.getLevel();
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pad).inflate(48);
		level.getEntitiesOfClass(Villager.class, box, e -> true).forEach(net.minecraft.world.entity.Entity::discard);
		level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box, e -> true).forEach(net.minecraft.world.entity.Entity::discard);
		var poi = level.getPoiManager();
		for (var rec : poi.getInRange(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), pad, 48,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			BlockPos p = rec.getPos().immutable();
			if (level.getBlockState(p).is(Blocks.BELL)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			else poi.remove(p);
		}
	}

	/** Drops every free HOME bed in range except the one this test placed inside the claim. */
	private static void clearForeignBeds(GameTestHelper h, BlockPos keepHead) {
		var level = h.getLevel();
		var poi = level.getPoiManager();
		for (var rec : poi.getInRange(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), keepHead, 48,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).toList()) {
			if (rec.getPos().equals(keepHead)) continue;
			BlockPos p = rec.getPos().immutable();
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			if (s.is(net.minecraft.tags.BlockTags.BEDS)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				BlockPos other = p.relative(s.getValue(net.minecraft.world.level.block.BedBlock.FACING),
						s.getValue(net.minecraft.world.level.block.BedBlock.PART)
								== net.minecraft.world.level.block.state.properties.BedPart.FOOT ? 1 : -1);
				if (level.getBlockState(other).is(net.minecraft.tags.BlockTags.BEDS)) level.setBlock(other, Blocks.AIR.defaultBlockState(), 3);
			} else poi.remove(p);
		}
	}

	private static long freeHomeBeds(GameTestHelper h, BlockPos around) {
		return h.getLevel().getPoiManager().getInRange(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME), around, 48,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).count();
	}

	private static String outsideDiag(GameTestHelper h, Villager[] v, BlockPos bedHead, net.minecraft.world.level.pathfinder.Path path) {
		return BreedTrace.summary() + " space " + spaceAbs(h, bedHead) + " path " + (path == null ? "null" : path.canReach()) + " pair " + v[0].blockPosition()
				+ " / " + v[1].blockPosition() + " bed " + bedHead + " blocks " + BreedingGate.blocks(v[0]) + " phase "
				+ Math.floorMod(h.getLevel().getDayTime(), 24000L) + " tick " + h.getTick();
	}

	private static void setBed(GameTestHelper h, BlockPos footAbs, net.minecraft.world.level.block.state.BlockState bedState) {
		h.getLevel().setBlock(footAbs, bedState.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
		h.getLevel().setBlock(footAbs.south(), bedState.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
	}

	// ---- T-L2-13 --------------------------------------------------------------------------------

	/** A licence from yesterday exempts nobody even when the dawn never ran (the Heart was unloaded over it). */
	@GameTest(environment = "villageheart-gametest:growth_stale", structure = YARD, skyAccess = true, maxTicks = 600)
	public void staleLicenceExemptsNobody(GameTestHelper h) {
		Villager[] v = new Villager[2];
		scene(h, 4, 0, 32).thenExecute(() -> {
			h.getLevel().setDayTime(dayStart(h) + 15000);
			for (int i = 0; i < 2; i++) {
				v[i] = adult(h, HOUSE1, 1 + 2 * i, 0);
				v[i].setNoAi(true);
				breadInPockets(v[i], 3);
			}
			License l = license(h, v[0], v[1]);
			h.assertTrue(BreedingGate.licenceOf(v[0]).isPresent(), "licensed today");
			// the day index changes but no dawn runs: the payroll already counts the new day
			long next = WageTestSupport.dayIndex(h) + 1;
			var data = dev.villageheart.world.VillageClaims.data(h.getLevel().getServer());
			data.setPayroll(data.payroll().orElseThrow().consumeDawn(next));
			h.getLevel().setDayTime(next * 24000L + 100);
			h.assertFalse(BreedTrace.finished(v[0].getUUID()), "(sanity)");
			h.assertTrue(l.bed().isPresent(), "the licence holds a bed");
		}).thenIdle(3).thenExecute(() -> {
			License l = licence(h).orElseThrow();
			h.assertTrue(l.state() == LicenceState.LAPSED && l.lapse().orElseThrow() == LapseReason.NEXT_DAWN, "the stale licence lapsed: " + describe(h));
			h.assertTrue(spaceAbs(h, BlockPos.of(l.bed().getAsLong())), "its bed was released: homes "
					+ v[0].getBrain().getMemory(MemoryModuleType.HOME) + " " + v[1].getBrain().getMemory(MemoryModuleType.HOME) + " bed " + BlockPos.of(l.bed().getAsLong()));
			for (Villager x : v) h.assertFalse(x.canBreed(), "nobody is exempt");
			end(h);
		});
	}
}
