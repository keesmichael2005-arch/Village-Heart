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
					h.assertValueEqual(1, l.seeds(), "the re-seed did not count a seed");
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
	 */
	@GameTest(environment = "villageheart-gametest:growth_outside_pair", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void outsidePairCannotTakeClaimBed(GameTestHelper h) {
		Villager[] v = new Villager[2];
		BlockPos[] bed = new BlockPos[1];
		int[] chunks = new int[4];
		GameTestSequence seq = scene(h, 2, 0, 32).thenExecute(() -> {
			HeartLocation hl = village(h).heart();
			bed[0] = new BlockPos(hl.x() + 76, hl.y(), hl.z()); // inside the claim (80), 6 blocks from the pair
			chunks[0] = (hl.x() + 70) >> 4;
			chunks[1] = (hl.x() + 96) >> 4;
			chunks[2] = (hl.z() - 16) >> 4;
			chunks[3] = (hl.z() + 16) >> 4;
			for (int cx = chunks[0]; cx <= chunks[1]; cx++) for (int cz = chunks[2]; cz <= chunks[3]; cz++) h.getLevel().setChunkForced(cx, cz, true);
		}).thenWaitUntil(() -> {
			for (int cx = chunks[0]; cx <= chunks[1]; cx++) for (int cz = chunks[2]; cz <= chunks[3]; cz++) {
				h.assertTrue(h.getLevel().isLoaded(new BlockPos(cx << 4, bed[0].getY(), cz << 4)), "chunk " + cx + "," + cz + " is loaded");
			}
		}).thenExecute(() -> {
			HeartLocation hl = village(h).heart();
			net.minecraft.world.level.block.state.BlockState bedState = Blocks.RED_BED.defaultBlockState()
					.setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
			// the claim's bed, and one home bed for each of the pair (so they do not go looking for the claim's)
			// the ground at both places (the Heart is a few blocks above it)
			int gy = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hl.x() + 82, hl.z());
			bed[0] = new BlockPos(bed[0].getX(), gy, bed[0].getZ());
			setBed(h, bed[0], bedState);
			BlockPos far = new BlockPos(hl.x() + 82, gy, hl.z());
			setBed(h, far.north(8), bedState);
			setBed(h, far.north(10), bedState);
			for (int i = 0; i < 2; i++) {
				v[i] = WorkerTestSupport.spawnVillager(h, new BlockPos(2, 0, 2));
				v[i].snapTo(far.getX() + 0.5 + i * 2, far.getY(), far.getZ() + 0.5, 0.0F, 0.0F);
				v[i].setAge(0);
				breadInPockets(v[i], 3);
			}
			h.getLevel().setDayTime(dayStart(h) + 100);
		}).thenWaitUntil(() -> h.assertTrue(h.getLevel().getPoiManager().getType(bed[0].south()).isPresent(), "the claim's bed has a POI"))
				.thenExecute(() -> {
					BlockPos far = new BlockPos(village(h).heart().x() + 82, bed[0].getY(), village(h).heart().z());
					claimBedAbs(h, v[0], far.north(8).south());
					claimBedAbs(h, v[1], far.north(10).south());
					h.assertFalse(BreedingGate.blocks(v[0]), "the pair is outside the claim: vanilla applies");
				});
		for (int i = 0; i < 40; i++) {
			seq = seq.thenExecuteAfter(30, () -> {
				if (!BreedTrace.finished(v[0].getUUID()) && !v[0].getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET) && v[0].canBreed() && v[1].canBreed()) {
					v[0].getBrain().setMemory(MemoryModuleType.BREED_TARGET, v[1]);
				}
			});
		}
		seq.thenExecute(() -> {
			var path = v[0].getNavigation().createPath(bed[0].south(), 1);
			h.assertTrue(GrowthStats.droppedBeds() >= 1, "vanilla's run took the claim's bed and the hook gave it back: " + BreedTrace.summary() + " space "
					+ spaceAbs(h, bed[0].south()) + " path " + (path == null ? "null" : path.canReach()) + " pair at " + v[0].blockPosition() + " bed " + bed[0] + " tick "
					+ h.getLevel().getGameTime() + " loaded " + h.getLevel().isLoaded(bed[0]) + " type " + h.getLevel().getPoiManager().getType(bed[0].south()));
			h.assertTrue(BreedTrace.aborts(BreedTrace.Abort.NO_BED, false) >= 1, "and the run ended as a NO_BED abort: " + BreedTrace.summary());
			h.assertTrue(h.getLevel().getPoiManager().getInSquare(t -> true, bed[0].south(), 0, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE)
					.anyMatch(r -> r.getPos().equals(bed[0].south())), "the claim's bed still has a free ticket");
			h.assertTrue(babies(h).isEmpty(), "no baby in the test area");
			for (Villager x : v) x.discard();
			for (int cx = chunks[0]; cx <= chunks[1]; cx++) for (int cz = chunks[2]; cz <= chunks[3]; cz++) h.getLevel().setChunkForced(cx, cz, false);
			end(h);
		});
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
