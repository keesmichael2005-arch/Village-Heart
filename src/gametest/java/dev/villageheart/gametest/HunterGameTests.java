package dev.villageheart.gametest;

import static dev.villageheart.gametest.HuntTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.building.Reason;
import dev.villageheart.core.building.Thing;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.hunt.HunterPlanner;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.gametest.MineTestSupport.Ascent;
import dev.villageheart.gametest.MineTestSupport.Tracker;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.hunt.HunterJob;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.WorkStats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Layer 2: the hunter (M17). Rules (docs/TESTING.md, "Hunter GameTests"): own environment per test ({@code hunt_<name>}), structure
 * {@code yard} with {@code skyAccess}, {@link HuntTestSupport#scene} first (it asserts the scene's premises), {@code VillageTestSupport.done}
 * + {@code WorkerTestSupport.end} last, measured bounds. Tests log their tick counts ({@code [m17] ...}).
 */
public class HunterGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static dev.villageheart.core.heart.VillagerRow row(GameTestHelper h, Villager v) {
		return HeartTestSupport.snapshot(h, dev.villageheart.core.Rank.OWNER).villagers().stream().filter(r -> r.id().equals(v.getUUID())).findFirst().orElseThrow();
	}

	private static GameTestSequence awaitAway(GameTestHelper h, GameTestSequence seq, Tracker tr, Villager[] v, int bound, String what) {
		return BuildingTestSupport.awaitWithin(h, seq, bound, what, () -> h.assertTrue(tr.away(), what + ": at " + v[0].position() + " "
				+ HunterJob.decisionOf(v[0].getUUID()) + " " + LumberTestSupport.status(h, v[0]) + " lodge " + BuildingTestSupport.reasons(h, lodgeMarker())));
	}

	private static WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	/** Trip length: from the hide to the step that brought it back. */
	private static long away(Tracker tr, int i) { return tr.ascents.get(i).record().gameTime() - tr.descents.get(i).since(); }

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	/** No weapon in the Lodge chest: the hunter does not go out ("Needs bow or sword"); a weapon put in sends it; taking it out brings it back. */
	@GameTest(environment = "villageheart-gametest:hunt_needs_weapon", structure = YARD, skyAccess = true, maxTicks = 3200)
	public void hunterNeedsWeapon(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		boolean[] everInvisible = new boolean[1];
		h.onEachTick(() -> {
			if (v[0] != null && v[0].isInvisible()) everInvisible[0] = true;
		});
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			// M19a: this test is about the old "Needs <tool>" row (no tool, nothing to make one from is a *different* row now); self-crafting is not its subject
			dev.villageheart.worker.gear.SelfCraftSwitch.off();
			h.getLevel().setDayTime(1000);
			v[0] = hire(h);
			tr.start();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the status becomes NEEDS_TOOL", () ->
				h.assertValueEqual(WorkerStatus.NEEDS_TOOL, status(h, v[0]), "status"));
		seq = seq.thenExecute(() -> {
			var text = dev.villageheart.worker.job.WorkerStatusText.of(WorkerStatus.NEEDS_TOOL, JobType.HUNTER);
			h.assertValueEqual("Needs bow or sword", ((net.minecraft.network.chat.contents.TranslatableContents) text.getContents()).getFallback(), "text");
			h.assertValueEqual(ActivityKind.NEEDS_TOOL, row(h, v[0]).activity(), "the Villagers tab row");
		});
		seq = seq.thenIdle(600);
		seq = seq.thenExecute(() -> {
			h.assertTrue(tr.descents.isEmpty() && !everInvisible[0], "never out without a weapon");
			putInChest(h, 0, sword("wood"));
			t0[0] = h.getTick();
		});
		seq = awaitAway(h, seq, tr, v, 350, "a wooden sword sends the hunter out");
		seq = seq.thenExecute(() -> System.out.println("[m17] hunterNeedsWeapon: out " + (h.getTick() - t0[0]) + " ticks after the sword was put in"));
		seq = seq.thenIdle(200);
		seq = seq.thenExecute(() -> {
			h.assertTrue(AwayTrips.isAway(v[0]), "still out");
			clearSlot(h, 0);
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the hunter comes back when the weapon is gone", () -> h.assertTrue(!tr.ascents.isEmpty(), "still out"));
		seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			h.assertValueEqual(SurfaceReason.NO_TOOL, a.record().reason(), "the reason");
			h.assertTrue(a.inventory().isEmpty(), "no yield: " + a.inventory());
			h.assertValueEqual(0L, AwayTrips.state(v[0]).rolls(), "no trip was rolled");
			h.assertTrue(a.progress() >= 160 && a.progress() <= 300, "progress is kept (1 unit a tick), got " + a.progress());
			h.assertValueEqual(WorkerStatus.NEEDS_TOOL, status(h, v[0]), "NEEDS_TOOL again");
			System.out.println("[m17] hunterNeedsWeapon: back " + (h.getTick() - t0[0]) + " ticks after the weapon was taken, progress " + a.progress());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-1 (roadmap) --------------------------------------------------------------------------

	/** Roadmap: a sword and a valid Hunting Ground give at least 4 raw meat and 2 leather in Storage within one day (exactly the predicted trips). */
	@GameTest(environment = "villageheart-gametest:hunt_sword_meat", structure = YARD, skyAccess = true, maxTicks = 11600)
	public void hunterWithSwordBringsMeatAndLeather(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] t0 = new long[1], trips0 = new long[1], returns0 = new long[1];
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			putInChest(h, 1, new ItemStack(Items.ARROW, 8));
			h.getLevel().setDayTime(1000);
			trips0[0] = WorkStats.get("hunter", "trips");
			returns0[0] = WorkStats.get("hunter", "returns");
			v[0] = hire(h);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = awaitAway(h, seq, tr, v, 400, "the hunter walks to the ground and vanishes");
		seq = seq.thenExecute(() -> System.out.println("[m17] hunterWithSwordBringsMeatAndLeather: out " + (h.getTick() - t0[0]) + " ticks after hiring"));
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 11600, "waiting for the end of the day"));
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			long rolls = AwayTrips.state(v[0]).rolls();
			StringBuilder log = new StringBuilder();
			for (int i = 0; i < tr.ascents.size(); i++) log.append(" trip").append(i).append(" out@").append(tr.descents.get(i).tick() - t0[0]).append(" back@")
					.append(tr.ascents.get(i).tick() - t0[0]).append(" away ").append(away(tr, i));
			System.out.println("[m17] hunterWithSwordBringsMeatAndLeather: " + rolls + " trips, meat " + meatInStorage(h) + ", leather " + count(h, "minecraft:leather") + ";" + log);
			h.assertValueEqual(2L, rolls, "two trips at level 1");
			h.assertTrue(meatInStorage(h) >= 4, "at least 4 raw meat in Storage, got " + meatInStorage(h));
			h.assertTrue(count(h, "minecraft:leather") >= 2, "at least 2 leather in Storage, got " + count(h, "minecraft:leather"));
			assertStorage(h, predicted(level, v[0].getUUID(), (int) rolls, i -> false), "exactly the predicted trips (sword: no arrow)");
			h.assertValueEqual(2, tr.descents.size(), "two departures");
			for (int i = 0; i < 2; i++) h.assertTrue(away(tr, i) >= 2975 && away(tr, i) <= 3025, "trip " + i + " lasted 3000 +- 25 ticks away, got " + away(tr, i));
			for (int i = 0; i < 2; i++) h.assertValueEqual(SurfaceReason.HAUL, tr.ascents.get(i).record().reason(), "reason of trip " + i);
			h.assertValueEqual(2L, WorkStats.get("hunter", "trips") - trips0[0], "work.hunter.trips");
			h.assertValueEqual(2L, WorkStats.get("hunter", "returns") - returns0[0], "work.hunter.returns");
			h.assertValueEqual((int) (2L * dev.villageheart.core.Balance.milliXpPerUnit(dev.villageheart.core.job.WorkUnit.GAME_HUNTED) / 1000),
					dev.villageheart.worker.WorkerAttachments.get(v[0]).orElseThrow().xp(), "M15: XP per completed trip (GAME_HUNTED)");
			h.assertValueEqual(10, chestSlot(h, 0).getDamageValue(), "5 durability per trip");
			h.assertValueEqual(8, chestSlot(h, 1).getCount(), "a sword takes no arrows");
			h.assertFalse(AwayTrips.isAway(v[0]), "back at the end of the day");
			var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, net.minecraft.world.phys.AABB.encapsulatingFullBlocks(h.absolutePos(new BlockPos(0, -6, 0)), h.absolutePos(new BlockPos(32, 14, 32))));
			h.assertTrue(items.isEmpty(), "no item entity lying around: " + items.stream().map(e -> e.getItem() + " at " + e.blockPosition()).toList());
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	/** A bow is chosen over a sword, takes one arrow per trip and brings exactly one extra meat. */
	@GameTest(environment = "villageheart-gametest:hunt_bow_arrows", structure = YARD, skyAccess = true, maxTicks = 9500)
	public void bowUsesOneArrowPerTrip(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] arrows0 = new long[1];
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			putInChest(h, 1, new ItemStack(Items.BOW));
			putInChest(h, 2, new ItemStack(Items.ARROW, 5));
			h.getLevel().setDayTime(1000);
			arrows0[0] = WorkStats.get("hunter", "arrows");
			v[0] = hire(h);
			tr.start();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 8500, "two trips are done and delivered", () -> {
			h.assertTrue(AwayTrips.state(v[0]).rolls() >= 2 && !AwayTrips.isAway(v[0]), "two trips back: " + AwayTrips.state(v[0]).rolls());
			var want = predicted(h.getLevel(), v[0].getUUID(), 2, i -> true);
			for (String id : dev.villageheart.core.hunt.HuntTable.allItems()) h.assertValueEqual((long) want.getOrDefault(id, 0), count(h, id), "Storage " + id);
		});
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			System.out.println("[m17] bowUsesOneArrowPerTrip: trips out@" + tr.descents.stream().map(d -> d.tick()).toList() + " away " + away(tr, 0) + " " + away(tr, 1));
			h.assertValueEqual(2L, AwayTrips.state(v[0]).rolls(), "two trips (the third does not fit the day)");
			h.assertValueEqual(3, inChest(h, Items.ARROW), "5 arrows - 2");
			h.assertValueEqual(10, chestSlot(h, 1).getDamageValue(), "the bow wore 5 per trip");
			h.assertValueEqual(0, chestSlot(h, 0).getDamageValue(), "the sword was not used");
			h.assertValueEqual(2L, WorkStats.get("hunter", "arrows") - arrows0[0], "work.hunter.arrows");
			int withBow = meat(predicted(level, v[0].getUUID(), 2, i -> true));
			int without = meat(predicted(level, v[0].getUUID(), 2, i -> false));
			h.assertValueEqual(without + 2, withBow, "the bow adds exactly one meat per trip");
			h.assertValueEqual((long) withBow, meatInStorage(h), "meat in Storage");
			for (int i = 0; i < 2; i++) h.assertTrue(away(tr, i) >= 2975 && away(tr, i) <= 3025, "trip " + i + " lasted 3000 +- 25, got " + away(tr, i));
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	/** A bow without arrows still hunts (no bonus); a tipped arrow put in later is taken by the next trip. */
	@GameTest(environment = "villageheart-gametest:hunt_bow_no_arrows", structure = YARD, skyAccess = true, maxTicks = 8500)
	public void bowWithoutArrowsHuntsWithoutBonus(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] arrows0 = new long[1];
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, new ItemStack(Items.BOW));
			h.getLevel().setDayTime(1000);
			arrows0[0] = WorkStats.get("hunter", "arrows");
			v[0] = hire(h);
			tr.start();
		});
		seq = seq.thenIdle(5);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(WorkerStatus.OK, status(h, v[0]), "a bow without arrows is not a missing tool");
			h.assertFalse(tr.away(), "not out yet");
		});
		seq = awaitAway(h, seq, tr, v, 400, "the hunter goes out");
		seq = seq.thenExecute(() -> {
			var r = row(h, v[0]);
			h.assertValueEqual(ActivityKind.AWAY_WORKING, r.activity(), "the Villagers tab row while out");
			h.assertValueEqual("Away (hunting)", dev.villageheart.core.heart.HeartTexts.EN.get(dev.villageheart.core.heart.HeartTexts.activityKey(r.activity(), r.jobId())), "text");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 3100, "the first trip ends", () -> h.assertTrue(!tr.ascents.isEmpty(), "still out"));
		seq = seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			h.assertTrue(away(tr, 0) >= 2975 && away(tr, 0) <= 3025, "3000 +- 25 ticks away, got " + away(tr, 0));
			h.assertValueEqual(predictedTrip(h.getLevel(), v[0].getUUID(), 0, false), a.inventory(), "the first trip's yield, without an arrow");
			h.assertValueEqual(0L, WorkStats.get("hunter", "arrows") - arrows0[0], "no arrow was taken");
			putInChest(h, 1, new ItemStack(Items.TIPPED_ARROW));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 4500, "the second trip ends", () -> h.assertTrue(tr.ascents.size() >= 2, "second trip still out"));
		seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(1);
			h.assertValueEqual(predictedTrip(h.getLevel(), v[0].getUUID(), 1, true), a.inventory(), "the second trip's yield, with the tipped arrow");
			h.assertTrue(chestSlot(h, 1).isEmpty(), "the tipped arrow was used: " + chestSlot(h, 1));
			h.assertValueEqual(10, chestSlot(h, 0).getDamageValue(), "bow wear");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	/** The field turns to stone while the hunter is out: it comes back, the Lodge and the ground say why; with the grass back it goes out again. */
	@GameTest(environment = "villageheart-gametest:hunt_ground_invalid", structure = YARD, skyAccess = true, maxTicks = 7500)
	public void groundInvalidWhileAway(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 400, "the hunter goes out");
		seq = seq.thenIdle(100);
		seq = seq.thenExecute(() -> {
			field(h, GROUND, false);
			h.assertTrue(natural(h, GROUND) < 40, "the field is stone now: " + natural(h, GROUND) + " natural cells");
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 1) + 40, "the hunter comes back", () -> h.assertTrue(!tr.ascents.isEmpty(), "still out"));
		seq = seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			System.out.println("[m17] groundInvalidWhileAway: back " + (h.getTick() - t0[0]) + " ticks after the field changed");
			h.assertValueEqual(SurfaceReason.SITE_INVALID, a.record().reason(), "the reason");
			h.assertFalse(a.invisible() || a.noAi() || a.invulnerable(), "visible, AI on");
			Vec3 want = Vec3.atBottomCenterOf(tr.descents.get(0).surface());
			h.assertTrue(a.record().pos().distanceTo(want) < 0.01, "at the saved surface spot " + a.record().pos() + " vs " + want);
			h.assertValueEqual(0.0, AwayTrips.state(v[0]).progress().units(), "the progress belonged to the old ground: reset");
			h.assertTrue(a.inventory().isEmpty(), "no yield: " + a.inventory());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 2) + 40, "the Lodge and the worker say why", () -> {
			h.assertValueEqual(WorkerStatus.WORKPLACE_INVALID, status(h, v[0]), "worker status");
			h.assertValueEqual(List.of(Reason.needsLinkedSite(BuildingType.HUNTING_GROUND)), BuildingTestSupport.reasons(h, lodgeMarker()), "the Lodge's reasons");
			h.assertValueEqual(List.of(Reason.needsMore(40 - natural(h, GROUND), Thing.NATURAL)), BuildingTestSupport.reasons(h, GROUND), "the ground's reasons");
		});
		seq = seq.thenIdle(2000);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1, tr.descents.size(), "never out again while the ground is invalid");
			field(h, GROUND, true);
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 2) + 400, "the hunter goes out again", () -> h.assertTrue(tr.descents.size() >= 2, "second departure"));
		seq.thenExecute(() -> {
			System.out.println("[m17] groundInvalidWhileAway: out again " + (h.getTick() - t0[0]) + " ticks after the grass came back");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	/** The departure cut-off (7700) and the daily cap, each on its own. */
	@GameTest(environment = "villageheart-gametest:hunt_cutoff_cap", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void departureCutoffAndDailyCap(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker[] tr = new Tracker[1];
		h.onEachTick(() -> {
			if (tr[0] != null) tr[0].sample(h);
		});
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			// (a+) hired at 7300: the trip still fits
			h.getLevel().setDayTime(7300);
			v[0] = hire(h);
			tr[0] = new Tracker(() -> v[0]);
			tr[0].start();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "(a+) a hunter hired at 7300 goes out", () -> h.assertTrue(tr[0].away(), "(a+) out"));
		seq = seq.thenExecute(() -> {
			long dt = h.getLevel().getDayTime() % 24000;
			System.out.println("[m17] departureCutoffAndDailyCap: (a+) hidden at day time " + dt);
			h.assertTrue(dt <= 7700, "hidden by 7700: " + dt);
			Workers.fire(h.getLevel(), v[0]);
			WorkerTestSupport.discardSpawned(h.getLevel());
			// (a-) hired at 7800: the trip would end after 11000 - 300
			h.getLevel().setDayTime(7800);
			v[0] = hire(h);
			tr[0] = new Tracker(() -> v[0]);
			tr[0].start();
		});
		seq = seq.thenWaitUntil(() -> {
			h.assertFalse(tr[0].away(), "(a-) never out after 7700");
			h.assertValueEqual(WorkerStatus.OK, status(h, v[0]), "(a-) no tool or work status");
			h.assertTrue(h.getLevel().getDayTime() % 24000 >= 10900, "(a-) waiting for 10900, now " + h.getLevel().getDayTime() % 24000);
		});
		seq = seq.thenExecute(() -> {
			h.assertTrue(tr[0].descents.isEmpty(), "(a-) it never went out");
			Workers.fire(h.getLevel(), v[0]);
			WorkerTestSupport.discardSpawned(h.getLevel());
			// (b-) day 1, two trips already done that day: no third
			h.getLevel().setDayTime(25000);
			v[0] = hire(h);
			AwayTrips.set(v[0], AwayTrips.state(v[0]).withDone(1L, 2));
			tr[0] = new Tracker(() -> v[0]);
			tr[0].start();
		});
		seq = seq.thenIdle(600);
		seq = seq.thenExecute(() -> {
			h.assertTrue(tr[0].descents.isEmpty(), "(b-) two trips done today: not out for 600 ticks");
			h.assertValueEqual(WorkerStatus.OK, status(h, v[0]), "(b-) done for the day is not a problem status");
			// (b+) the same count, but saved for day 0
			AwayTrips.set(v[0], AwayTrips.state(v[0]).withDone(0L, 2));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "(b+) a count saved for another day does not count", () -> h.assertTrue(tr[0].away(), "(b+) out"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	/** The Heart row says "Away (hunting)"; nothing (the miner system included) surfaces a hunter for 200 ticks; fired while away it comes up at once. */
	@GameTest(environment = "villageheart-gametest:hunt_heart_fire", structure = YARD, skyAccess = true, maxTicks = 2400)
	public void heartShowsAwayHuntingAndFireWhileAway(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		GameTestSequence seq = scene(h, false).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 400, "the hunter goes out");
		seq = seq.thenExecute(() -> {
			var r = row(h, v[0]);
			h.assertValueEqual(ActivityKind.AWAY_WORKING, r.activity(), "activity");
			h.assertTrue(!r.name().isEmpty(), "it has its name: '" + r.name() + "'");
			h.assertTrue(r.loaded(), "loaded");
			h.assertValueEqual("Away (hunting)", dev.villageheart.core.heart.HeartTexts.EN.get(dev.villageheart.core.heart.HeartTexts.activityKey(r.activity(), r.jobId())), "text");
		});
		seq = seq.thenIdle(200);
		seq = seq.thenExecute(() -> {
			h.assertTrue(AwayTrips.isAway(v[0]) && tr.ascents.isEmpty(), "still out after 200 ticks (nothing but the hunter system steps a hunter)");
			Vec3 want = Vec3.atBottomCenterOf(tr.descents.get(0).surface());
			h.assertTrue(Workers.fire(h.getLevel(), v[0]), "fired");
			h.assertFalse(AwayTrips.isAway(v[0]), "no trip");
			h.assertFalse(v[0].isInvisible() || v[0].isNoAi() || v[0].isInvulnerable() || v[0].isSilent(), "visible, AI on");
			h.assertTrue(v[0].position().distanceTo(want) < 0.01, "at the surface spot " + v[0].position() + " vs " + want);
			h.assertFalse(dev.villageheart.worker.WorkerAttachments.isWorker(v[0]), "unemployed");
			h.assertTrue(v[0].getInventory().isEmpty(), "no yield");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	/** Two hunters share one ground: different cells, the first-hired holds the bow and is the only one to take an arrow. */
	@GameTest(environment = "villageheart-gametest:hunt_two_hunters", structure = YARD, skyAccess = true, maxTicks = 5200)
	public void twoHuntersOneGround(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Tracker t1 = new Tracker(() -> v[0]), t2 = new Tracker(() -> v[1]);
		h.onEachTick(() -> {
			t1.sample(h);
			t2.sample(h);
		});
		GameTestSequence seq = scene(h, true).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			putInChest(h, 1, new ItemStack(Items.BOW));
			putInChest(h, 2, new ItemStack(Items.ARROW, 2));
			h.getLevel().setDayTime(1000);
			v[0] = hireAt(h, spawnSpot());
			v[1] = hireAt(h, spawnSpot2());
			t1.start();
			t2.start();
		});
		long[] w0 = new long[1];
		seq = seq.thenExecute(() -> w0[0] = h.getTick());
		seq = seq.thenWaitUntil(() -> {
			if (h.getTick() - w0[0] == 460) {
				System.out.println("[m17] twoHunters diagnostics at +460: out " + t1.descents.size() + "/" + t2.descents.size() + ", back "
						+ t1.ascents.stream().map(a -> a.record().reason()).toList() + "/" + t2.ascents.stream().map(a -> a.record().reason()).toList()
						+ "; at " + v[0].position() + " / " + v[1].position() + "; " + HunterJob.decisionOf(v[0].getUUID()) + " / " + HunterJob.decisionOf(v[1].getUUID())
						+ "; " + status(h, v[0]) + " / " + status(h, v[1]) + "; away " + t1.away() + " " + t2.away());
			}
			h.assertTrue(t1.away() && t2.away(), "both out: " + t1.away() + " " + t2.away());
		});
		seq = seq.thenExecute(() -> h.assertTrue(h.getTick() - w0[0] <= 450, "both hunters are out took " + (h.getTick() - w0[0]) + " ticks, bound 450"));
		seq = seq.thenExecute(() -> {
			BlockPos a = v[0].blockPosition(), b = v[1].blockPosition(), marker = h.absolutePos(GROUND);
			System.out.println("[m17] twoHuntersOneGround: held at " + a + " and " + b + ", marker " + marker);
			h.assertTrue(!a.equals(b), "different cells");
			h.assertTrue(!a.equals(marker) && !b.equals(marker), "neither on the marker cell");
			var h1 = AwayTrips.state(v[0]).trip().orElseThrow().hold();
			var h2 = AwayTrips.state(v[1]).trip().orElseThrow().hold();
			h.assertTrue(!h1.equals(h2), "different hold spots " + h1 + " " + h2);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 3300, "both hunters are back", () -> h.assertTrue(!t1.ascents.isEmpty() && !t2.ascents.isEmpty(), "back: " + t1.ascents.size() + " " + t2.ascents.size()));
		seq.thenExecute(() -> {
			System.out.println("[m17] twoHuntersOneGround: away " + away(t1, 0) + " and " + away(t2, 0));
			h.assertTrue(away(t1, 0) >= 2975 && away(t1, 0) <= 3025, "the first was away 3000 +- 25, got " + away(t1, 0));
			h.assertTrue(away(t2, 0) >= 2975 && away(t2, 0) <= 3025, "the second was away 3000 +- 25, got " + away(t2, 0));
			h.assertValueEqual(predictedTrip(h.getLevel(), v[0].getUUID(), 0, true), t1.ascents.get(0).inventory(), "the bow hunter's yield (with the arrow)");
			h.assertValueEqual(predictedTrip(h.getLevel(), v[1].getUUID(), 0, false), t2.ascents.get(0).inventory(), "the sword hunter's yield (no arrow)");
			h.assertValueEqual(1, inChest(h, Items.ARROW), "only the bow hunter took an arrow");
			h.assertValueEqual(5, chestSlot(h, 1).getDamageValue(), "the bow wore 5");
			h.assertValueEqual(5, chestSlot(h, 0).getDamageValue(), "the sword wore 5");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/**
	 * A hunter that is already out stands (hidden) where it arrived. In a 1-wide corridor on the way to the marker the next hunter walks
	 * against it (a NoAI villager does not give way). It must not push on for hundreds of ticks: it counts as arrived when it touches an
	 * away villager near the ground and goes out beside it (35 ticks; without that rule 133, and up to 2600 in the full suite under load,
	 * where {@code twoHuntersOneGround} lost 4 x 600 ticks to retries).
	 */
	@GameTest(environment = "villageheart-gametest:hunt_blocked", structure = YARD, skyAccess = true, maxTicks = 1400)
	public void hunterBlockedByHiddenHunterGoesOutBesideIt(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Tracker tr = new Tracker(() -> v[1]);
		h.onEachTick(() -> tr.sample(h));
		BlockPos cellA = new BlockPos(15, 0, 14), startB = new BlockPos(15, 0, 10);
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, true).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			putInChest(h, 1, sword("stone"));
			// a corridor one block wide on x = 15, z 11 .. 14, ending in the cell where the first hunter hides
			for (int z = 11; z <= 14; z++) for (int y = 0; y <= 2; y++) {
				h.setBlock(new BlockPos(14, y, z), net.minecraft.world.level.block.Blocks.STONE);
				h.setBlock(new BlockPos(16, y, z), net.minecraft.world.level.block.Blocks.STONE);
			}
			h.getLevel().setDayTime(1000);
			v[0] = hire(h);
			var abs = h.absolutePos(cellA);
			v[0].teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
			var loc = BuildingTestSupport.loc(h, GROUND);
			AwayTrips.set(v[0], AwayTrips.state(v[0]).withProgress(dev.villageheart.core.mine.AwayProgress.ZERO, java.util.Optional.of(loc)));
			AwayTrips.hide(h.getLevel(), v[0], "hunter", loc, abs);
			h.assertTrue(AwayTrips.isAway(v[0]), "the first hunter is out, in the corridor");
		});
		seq = seq.thenExecute(() -> {
			v[1] = hireAt(h, spawnSpot2());
			var abs = h.absolutePos(startB);
			v[1].teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 80, "the second hunter goes out beside the first", () -> h.assertTrue(tr.away(),
				"out: at " + v[1].position() + " " + HunterJob.decisionOf(v[1].getUUID()) + " " + status(h, v[1])));
		seq.thenExecute(() -> {
			System.out.println("[m17] hunterBlockedByHiddenHunterGoesOutBesideIt: out " + (h.getTick() - t0[0]) + " ticks after the start, at " + v[1].blockPosition());
			h.assertTrue(!v[1].blockPosition().equals(v[0].blockPosition()), "a different cell than the first hunter");
			h.assertTrue(AwayTrips.isAway(v[0]), "the first hunter is still out");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	/** A hunter more than 40 blocks from its ground walks to the Lodge first, then out. */
	@GameTest(environment = "villageheart-gametest:hunt_far_hunter", structure = YARD, skyAccess = true, maxTicks = 2600)
	public void farHunterWalksViaLodge(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		List<HunterPlanner.Action> trace = new ArrayList<>();
		h.onEachTick(() -> {
			if (v[0] == null) return;
			var d = HunterJob.decisionOf(v[0].getUUID());
			if (d != null && (trace.isEmpty() || trace.get(trace.size() - 1) != d.action())) trace.add(d.action());
		});
		BlockPos far = new BlockPos(6, 0, -10);
		java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> saved = new java.util.LinkedHashMap<>();
		java.util.Set<net.minecraft.world.level.ChunkPos> forced = new java.util.LinkedHashSet<>();
		long[] t0 = new long[1];
		// the far spot lies outside the structure: whether its chunk is entity-ticking depends on where the test was placed, so force it
		// right away (a forced chunk takes up to ~70 ticks to tick entities; the scene needs about as long to build). Every chunk the strip
		// touches, not only those of its two corners: where a chunk border runs between x = 5 and 6 and another crosses the strip in z, the
		// far spot's chunk is neither corner's and stayed at block-ticking level (the "not entity-ticking yet" flake, backlog-2)
		for (int x = 5; x <= 7; x++) for (int z = -10; z <= -1; z++) forced.add(new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(x, 0, z))));
		h.assertTrue(forced.contains(new net.minecraft.world.level.ChunkPos(h.absolutePos(far))), "premise: the far spot's chunk is forced");
		for (var c : forced) h.getLevel().setChunkForced(c.x, c.z, true);
		GameTestSequence seq = scene(h, false, GROUND_FAR).thenExecute(() -> {
			lightYard(h);
			putInChest(h, 0, sword("stone"));
			// a 3-wide stone strip from the far spot to the footprint, so the walk is not decided by what stands there
			for (int x = 5; x <= 7; x++) for (int z = -10; z <= -1; z++) for (int y = -1; y <= 3; y++) {
				BlockPos p = new BlockPos(x, y, z);
				saved.put(p, h.getBlockState(p));
				h.setBlock(p, y == -1 ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.AIR);
			}
			h.getLevel().setDayTime(1000);
			v[0] = hire(h);
			var abs = h.absolutePos(far);
			double dist = Vec3.atBottomCenterOf(abs).distanceTo(Vec3.atBottomCenterOf(h.absolutePos(GROUND_FAR)));
			h.assertTrue(dist > 40, "the far spot is more than 40 blocks from the marker: " + dist);
			v[0].teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 60, "premise: the far spot is entity-ticking (a forced chunk), else the hunter would not act there",
				() -> h.assertTrue(h.getLevel().isPositionEntityTicking(v[0].blockPosition()), "the far spot is not entity-ticking yet (hunter at "
						+ v[0].blockPosition() + ", chunk " + new net.minecraft.world.level.ChunkPos(v[0].blockPosition()) + ", forced " + forced + ")"));
		seq = awaitAway(h, seq, tr, v, 700, "the hunter walks in and goes out");
		seq.thenExecute(() -> {
			System.out.println("[m17] farHunterWalksViaLodge: out " + (h.getTick() - t0[0]) + " ticks after the teleport, plan trace " + trace);
			int walk = trace.indexOf(HunterPlanner.Action.WALK_TO_LODGE), out = trace.indexOf(HunterPlanner.Action.GO_OUT);
			h.assertTrue(walk >= 0 && out > walk, "WALK_TO_LODGE comes before GO_OUT: " + trace);
			saved.forEach((p, s) -> h.setBlock(p, s));
			for (var c : forced) h.getLevel().setChunkForced(c.x, c.z, false);
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
