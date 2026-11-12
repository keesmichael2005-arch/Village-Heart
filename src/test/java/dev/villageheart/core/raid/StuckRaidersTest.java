package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.Balance;
import dev.villageheart.core.raid.StuckRaiders.Action;
import dev.villageheart.core.raid.StuckRaiders.Result;
import dev.villageheart.core.raid.StuckRaiders.Watch;
import org.junit.jupiter.api.Test;

/** The stuck-raider rule (owner, 2026-11-11): glow at 12000 ticks without progress, flee 1200 ticks later, never when moving or fighting. */
class StuckRaidersTest {
	private static final int STUCK = Balance.STUCK_RAIDER_TICKS;
	private static final int GRACE = Balance.STUCK_FLEE_GRACE_TICKS;
	private static final double PROGRESS = Balance.STUCK_PROGRESS_BLOCKS;

	private static Result step(Watch w, double dist, boolean fighting, boolean helper, long now) {
		return StuckRaiders.step(w, dist, fighting, helper, now, STUCK, GRACE, PROGRESS);
	}

	@Test
	void theDefaultsAreTheOwnersNumbers() {
		assertEquals(12000, Balance.STUCK_RAIDER_TICKS);
		assertEquals(1200, Balance.STUCK_FLEE_GRACE_TICKS);
		assertEquals(16.0, Balance.STUCK_HELPER_RANGE);
	}

	@Test
	void firstSightOnlyStartsTheClock() {
		Result r = step(null, 80, false, false, 500);
		assertEquals(Action.NONE, r.action());
		assertNotNull(r.watch());
		assertEquals(500, r.watch().progressAt());
	}

	@Test
	void noProgressForTheLimitMakesItGlow() {
		Watch w = Watch.start(80, 0);
		assertEquals(Action.NONE, step(w, 80, false, false, STUCK - 1).action());
		Result r = step(w, 80, false, false, STUCK);
		assertEquals(Action.GLOW, r.action());
		assertTrue(r.watch().glowing());
		assertEquals(STUCK, r.watch().glowAt());
	}

	@Test
	void stillStuckAfterTheGraceFlees() {
		Watch glowing = new Watch(80, 0, STUCK);
		assertEquals(Action.NONE, step(glowing, 80, false, false, STUCK + GRACE - 1).action());
		assertEquals(Action.FLEE, step(glowing, 80, false, false, STUCK + GRACE).action());
	}

	@Test
	void aRaiderThatWalksTowardTheVillageIsNeverStuck() {
		Watch w = Watch.start(100, 0);
		long now = 0;
		double d = 100;
		for (int i = 0; i < 40; i++) {
			now += 1000;
			d -= 3; // 3 blocks closer every 1000 ticks, over 40000 ticks
			Result r = step(w, d, false, false, now);
			assertEquals(Action.NONE, r.action(), "tick " + now);
			w = r.watch();
		}
	}

	@Test
	void wanderingAroundWithoutGettingCloserStillCountsAsStuck() {
		Watch w = Watch.start(80, 0);
		assertEquals(Action.GLOW, step(w, 81.5, false, false, STUCK).action());
	}

	@Test
	void aFightingRaiderIsNeverStuckAndItsClockRestarts() {
		Watch w = Watch.start(80, 0);
		Result r = step(w, 80, true, false, STUCK * 2);
		assertEquals(Action.NONE, r.action());
		assertEquals(STUCK * 2, r.watch().progressAt());
	}

	@Test
	void someoneNearResetsTheClock() {
		Watch w = Watch.start(80, 0);
		Result r = step(w, 80, false, true, STUCK + 5);
		assertEquals(Action.NONE, r.action());
		assertEquals(STUCK + 5, r.watch().progressAt());
		assertEquals(Action.NONE, step(r.watch(), 80, false, false, STUCK + 10).action());
	}

	@Test
	void aGlowingRaiderThatIsFoundStopsGlowingAndIsNotRemoved() {
		Watch glowing = new Watch(80, 0, STUCK);
		Result r = step(glowing, 80, false, true, STUCK + GRACE + 50);
		assertEquals(Action.CLEAR_GLOW, r.action());
		assertTrue(!r.watch().glowing());
	}

	@Test
	void aGlowingRaiderThatMovesStopsGlowing() {
		Watch glowing = new Watch(80, 0, STUCK);
		assertEquals(Action.CLEAR_GLOW, step(glowing, 60, false, false, STUCK + 100).action());
	}

	@Test
	void theBestDistanceNeverGrows() {
		Watch w = Watch.start(80, 0);
		Result r = step(w, 70, false, false, 10);
		assertEquals(70, r.watch().best());
		Result back = step(r.watch(), 90, false, false, 20);
		assertEquals(70, back.watch().best());
	}
}
