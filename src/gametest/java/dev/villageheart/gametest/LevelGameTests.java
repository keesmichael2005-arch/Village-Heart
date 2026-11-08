package dev.villageheart.gametest;

import static dev.villageheart.gametest.LevelTestSupport.data;
import static dev.villageheart.gametest.LevelTestSupport.progress;
import static dev.villageheart.gametest.LevelTestSupport.put;
import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.Tier;
import dev.villageheart.core.job.Progress;
import dev.villageheart.core.job.XpChange;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Layer 2: job levels and XP (M15). Own environment per test ({@code levels_<name>}), the lumber scene, {@code begin}/{@code done}/{@code end}.
 * Levels are set by writing the attachment where a test needs a start state.
 */
public class LevelGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static int badge(Villager v) { return v.getVillagerData().level(); }

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_add_xp", structure = YARD, skyAccess = true, maxTicks = 600)
	public void addXpLevelsUpAndMirrors(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			v[0] = hire(h, spawnSpot());
			h.assertValueEqual(new Progress(1, 0), progress(v[0]), "a hire starts at level 1, 0 XP");
			h.assertValueEqual(1, badge(v[0]), "badge 1");
			Optional<XpChange> a = Workers.addXp(v[0], 99);
			h.assertValueEqual(new Progress(1, 99), progress(v[0]), "99 XP: still level 1");
			h.assertTrue(a.isPresent() && !a.get().levelledUp(), "no level-up at 99");
			Optional<XpChange> b = Workers.addXp(v[0], 1);
			h.assertValueEqual(new Progress(2, 100), progress(v[0]), "100 XP: level 2, in the same tick");
			h.assertValueEqual(2, badge(v[0]), "the vanilla level badge follows in the same tick");
			h.assertTrue(b.isPresent() && b.get().levelledUp(), "level-up reported");
			Optional<XpChange> c = Workers.addXp(v[0], 500);
			h.assertValueEqual(new Progress(2, 300), progress(v[0]), "Village caps at level 2; XP is banked to 300");
			h.assertTrue(c.isPresent() && c.get().reachedCap() && !c.get().levelledUp(), "the cap was reached");
			h.assertValueEqual(2, badge(v[0]), "badge stays 2");
			Optional<XpChange> zero = Workers.addXp(v[0], 0);
			h.assertTrue(zero.isPresent() && !zero.get().levelledUp() && !zero.get().reachedCap(), "0 XP changes nothing");
			Villager stranger = WorkerTestSupport.spawnVillager(h, spawnSpot());
			h.assertTrue(Workers.addXp(stranger, 50).isEmpty(), "a non-worker gets nothing");
			h.assertTrue(stranger.getAttached(WorkerAttachments.WORKER) == null, "and no attachment");
			try {
				Workers.addXp(v[0], -1);
				h.assertTrue(false, "negative XP must throw");
			} catch (IllegalArgumentException expected) {
				// fine
			}
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_tier_up", structure = YARD, skyAccess = true, maxTicks = 800)
	public void tierUpTurnsBankedXpIntoLevels(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, true, false).thenExecute(() -> {
			v[0] = hire(h, spawnSpot());
			v[1] = hire(h, spawnSpot());
			put(v[0], 1, 300);
			put(v[1], 3, 0);
		});
		seq = seq.thenIdle(45);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(new Progress(1, 300), progress(v[0]), "at Hamlet the banked XP does not make a level");
			h.assertValueEqual(1, badge(v[0]), "badge 1");
			h.assertValueEqual(new Progress(3, 0), progress(v[1]), "a level above the cap is kept at Hamlet (the pass never lowers)");
			LevelTestSupport.tier(h, Tier.VILLAGE);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 21, "the level pass after the tier-up",
				() -> h.assertValueEqual(2, data(v[0]).level(), "level after setTier(VILLAGE)"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(2, badge(v[0]), "badge 2");
			LevelTestSupport.tier(h, Tier.TOWN);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 21, "the level pass after the second tier-up",
				() -> h.assertValueEqual(3, data(v[0]).level(), "level after setTier(TOWN)"));
		seq.thenExecute(() -> {
			h.assertValueEqual(new Progress(3, 300), progress(v[0]), "XP kept");
			h.assertValueEqual(3, badge(v[0]), "badge 3");
			h.assertValueEqual(new Progress(3, 0), progress(v[1]), "the level-3 worker is untouched");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_reload_fire", structure = YARD, skyAccess = true, maxTicks = 600)
	public void levelSurvivesReloadAndFireResets(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			v[0] = hire(h, spawnSpot());
			put(v[0], 2, 150);
			v[0] = reloadEntity(h.getLevel(), v[0]);
			h.assertValueEqual(new Progress(2, 150), progress(v[0]), "level and XP survive a save and load");
			h.assertValueEqual(2, badge(v[0]), "the badge survives too");
			h.assertTrue(Workers.fire(h.getLevel(), v[0]), "fired");
			h.assertTrue(v[0].getAttached(WorkerAttachments.WORKER) == null, "no attachment after firing");
			h.assertValueEqual(1, badge(v[0]), "vanilla level 1 after firing");
			h.assertTrue(v[0].getVillagerData().profession().is(VillagerProfession.NONE), "unemployed");
			HireResult r = Workers.hire(h.getLevel(), v[0], JobType.LUMBERJACK, Optional.empty());
			h.assertTrue(r instanceof HireResult.Hired, "rehired: " + r);
			h.assertValueEqual(new Progress(1, 0), progress(v[0]), "a rehire starts at level 1, 0 XP");
			h.assertValueEqual(1, badge(v[0]), "badge 1");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_cured_zombie", structure = YARD, skyAccess = true, maxTicks = 900)
	public void curedZombieWorkerIsNotAWorker(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		java.util.UUID[] id = new java.util.UUID[1];
		net.minecraft.world.entity.monster.zombie.ZombieVillager[] zv = new net.minecraft.world.entity.monster.zombie.ZombieVillager[1];
		GameTestSequence seq = scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			v[0] = hire(h, spawnSpot());
			put(v[0], 2, 150);
			id[0] = v[0].getUUID();
			net.minecraft.world.entity.monster.zombie.Zombie zombie = h.spawn(EntityType.ZOMBIE, spawnSpot());
			zombie.setNoAi(true);
			h.assertTrue(zombie.convertVillagerToZombieVillager(h.getLevel(), v[0]), "converted");
			var zombies = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.ZombieVillager.class,
					new net.minecraft.world.phys.AABB(h.absolutePos(spawnSpot())).inflate(3));
			h.assertValueEqual(1, zombies.size(), "one zombie villager");
			zv[0] = zombies.get(0);
			zv[0].setNoAi(true);
			zombie.discard();
			h.assertTrue(zv[0].getVillagerData().profession().is(VillagerProfession.NONE), "no mod profession on the zombie villager");
			h.assertValueEqual(1, zv[0].getVillagerData().level(), "no worker level badge on the zombie villager");
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(id[0]).isEmpty(), "record gone");
			// the vanilla cure path: finishConversion copies the zombie's VillagerData onto a new villager
			try {
				java.lang.reflect.Method start = net.minecraft.world.entity.monster.zombie.ZombieVillager.class
						.getDeclaredMethod("startConverting", java.util.UUID.class, int.class);
				start.setAccessible(true);
				start.invoke(zv[0], null, 5);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("could not start the cure", e);
			}
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "the cure",
				() -> h.assertTrue(cured(h) != null, "the zombie villager was cured into a villager"));
		seq.thenExecute(() -> {
			Villager c = cured(h);
			h.assertTrue(c != null, "the cured villager exists");
			WorkerTestSupport.track(c);
			h.assertTrue(c.getAttached(WorkerAttachments.WORKER) == null, "no attachment on the cured villager");
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(c.getUUID()).isEmpty(), "no record");
			h.assertTrue(c.getVillagerData().profession().is(VillagerProfession.NONE), "no mod profession: " + c.getVillagerData().profession().getRegisteredName());
			h.assertValueEqual(1, c.getVillagerData().level(), "vanilla level 1");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The villager the cure produced: any live villager around the camp (the converted one was discarded). */
	private static Villager cured(GameTestHelper h) {
		var box = new net.minecraft.world.phys.AABB(h.absolutePos(spawnSpot())).inflate(10);
		var list = h.getLevel().getEntitiesOfClass(Villager.class, box, e -> !e.isRemoved());
		return list.isEmpty() ? null : list.get(0);
	}
}
