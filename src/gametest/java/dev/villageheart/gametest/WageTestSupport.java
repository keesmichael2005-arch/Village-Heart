package dev.villageheart.gametest;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.economy.Payroll;
import dev.villageheart.core.time.DailyClock;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.economy.VillageDawn;
import dev.villageheart.economy.WageStats;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Helpers for the wage GameTests (M12). Rules (docs/TESTING.md, "Wage GameTests"): own {@code wages_<name>} environment, the {@code yard} structure
 * with {@code skyAccess}, {@link WorkerTestSupport#begin} first (it turns {@code advance_time} on: the day clock must run), the scene, hires
 * with wages still OFF (a hire is charged once wages are on), then {@link NeedsTestHooks#enableWages}; {@link #toDawn} crosses a day change;
 * {@code VillageTestSupport.done} and {@code WorkerTestSupport.end} last. Yards are lit where a test crosses night time.
 */
public final class WageTestSupport {
	private WageTestSupport() {}

	public static final BlockPos CAMP = LumberTestSupport.CAMP;
	public static final BlockPos STORE = LumberTestSupport.STORE;
	/** A Farmhouse south of the camp (the camp and the Storage share the north row). */
	public static final BlockPos FARM = new BlockPos(8, 0, 14);

	public static String env(String name) { return "villageheart-gametest:wages_" + name; }

	public static void log(String s) { System.out.println("[m12] " + s); }

	public static BlockPos farmMarker() { return BuildingTestSupport.markerPos(FARM, 5); }

	/** Interior cell of the camp / the Farmhouse to spawn a villager in. */
	public static BlockPos campSpawn(int i) { return BuildingTestSupport.inside(CAMP, 1 + i, 0, 1); }

	public static BlockPos farmSpawn() { return BuildingTestSupport.inside(FARM, 1, 0, 1); }

	/**
	 * Claim, a Lumber Camp with {@code chops} (1 or 2) Chopping Blocks, a Storage and (when {@code composters > 0}) a Farmhouse with that many
	 * composters, all valid. Wages are still off. Call {@code WorkerTestSupport.begin(h)} before it.
	 */
	public static GameTestSequence scene(GameTestHelper h, int chops, int composters) {
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			NeedsTestSupport.sweepVillagers(h);
			LumberTestSupport.sweepStrayTrees(h, 48);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			LumberTestSupport.buildCamp(h);
			if (chops >= 2) LumberTestSupport.addSecondChop(h);
			LumberTestSupport.buildStorage(h);
			LumberTestSupport.placeMarkers(h, true);
			if (composters > 0) {
				WorkerTestSupport.farmhouse(h, FARM, composters);
				BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, farmMarker(), Direction.NORTH);
			}
		}).thenWaitUntil(() -> {
			LumberTestSupport.assertValid(h, true);
			if (composters > 0) BuildingTestSupport.assertStatus(h, farmMarker(), dev.villageheart.block.MarkerStatus.VALID);
		});
	}

	public static Villager hire(GameTestHelper h, JobType job, BlockPos spawnRel) {
		Villager v = WorkerTestSupport.spawnVillager(h, spawnRel);
		HireResult r = Workers.hire(h.getLevel(), v, job, Optional.empty());
		h.assertTrue(r instanceof HireResult.Hired, "the " + job.id() + " was hired: " + r);
		return v;
	}

	public static Villager hireLumberjack(GameTestHelper h, int spawnIndex) { return hire(h, JobType.LUMBERJACK, campSpawn(spawnIndex)); }

	public static Villager hireFarmer(GameTestHelper h) { return hire(h, JobType.FARMER, farmSpawn()); }

	public static VillageState village(GameTestHelper h) { return VillageClaims.current(h.getLevel().getServer()).orElseThrow(); }

	public static Payroll payroll(GameTestHelper h) { return VillageClaims.data(h.getLevel().getServer()).payroll().orElseThrow(); }

	public static int treasury(GameTestHelper h) { return village(h).treasury(); }

	public static WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	public static boolean paid(Villager v) {
		return v.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER).needs().paid();
	}

	public static void wagesOn() { NeedsTestHooks.enableWages(); }

	public static void wagesOff() { dev.villageheart.needs.NeedsOverride.wagesEnabled = false; }

	public static void deposit(GameTestHelper h, int n) { VillageClaims.depositTreasury(h.getLevel().getServer(), n); }

	public static long dayIndex(GameTestHelper h) { return DailyClock.dayIndex(h.getLevel().getDayTime()); }

	/**
	 * Crosses one day change: the clock goes to 23 980 of today, and the sequence waits (bound 60 ticks, measured) until the payroll's
	 * {@code lastWageDay} is the next day index. {@code out[0]} holds the {@link PayResult} of the run, or stays null when wages are off
	 * (the day is consumed, nobody is paid).
	 */
	public static GameTestSequence toDawn(GameTestHelper h, GameTestSequence seq, PayResult[] out) {
		long[] want = new long[1];
		GameTestSequence s = seq.thenExecute(() -> {
			long idx = dayIndex(h);
			want[0] = idx + 1;
			out[0] = null;
			WageStats.setTestListener(r -> out[0] = r);
			h.getLevel().setDayTime(idx * 24000L + 23980);
		});
		return BuildingTestSupport.awaitWithin(h, s, 60, "the day change", () -> {
			h.assertTrue(dayIndex(h) >= want[0], "the clock crossed into day index " + want[0]);
			h.assertValueEqual(want[0], payroll(h).lastWageDay(), "the payroll moved to the new day");
		});
	}

	/** Today's clock without moving the day index: the day time goes to {@code phase} of the current day. */
	public static void setPhase(GameTestHelper h, int phase) {
		h.getLevel().setDayTime(dayIndex(h) * 24000L + phase);
	}

	/** Distance of a villager from the bottom centre of an ABSOLUTE block (what the tree helpers return). */
	public static double distAbs(Villager v, BlockPos abs) { return v.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(abs)); }

	/** Distance of a villager from the bottom centre of a (relative) block. */
	public static double dist(GameTestHelper h, Villager v, BlockPos rel) { return WorkerTestSupport.distance(h, v, rel); }

	public static void done(GameTestHelper h) {
		VillageDawn.removeTestListeners();
		WageStats.setTestListener(null);
		VillageTestSupport.done(h);
		WorkerTestSupport.end(h);
	}
}
