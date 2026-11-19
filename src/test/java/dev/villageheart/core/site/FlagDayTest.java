package dev.villageheart.core.site;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

/** The walk-home clock for a new tree and a new hunt. */
class FlagDayTest {
	@Test
	void aBlockIsTenTicks() {
		assertEquals(0, FlagDay.walkTicks(0));
		assertEquals(10, FlagDay.walkTicks(1));
		assertEquals(25, FlagDay.walkTicks(2.5));
	}

	@Test
	void aShortWalkLeavesTheLumberDay() {
		assertEquals(Balance.LUMBER_LAST_FELL, FlagDay.treeCutoff(0));
		assertEquals(Balance.LUMBER_LAST_FELL, FlagDay.treeCutoff(10), "100 ticks of walking still ends after 5000");
		assertTrue(FlagDay.newTree(4999, 0));
		assertFalse(FlagDay.newTree(5000, 0));
		assertTrue(FlagDay.newTree(4999, 10));
	}

	@Test
	void aLongWalkStopsANewTreeEarlier() {
		// 700 blocks is 7000 ticks. Work ends at the meeting, 9000, so 9000 - 7000 = 2000, which is before 5000.
		assertEquals(2000, FlagDay.treeCutoff(700));
		assertTrue(FlagDay.newTree(1999, 700));
		assertFalse(FlagDay.newTree(2000, 700));
		assertFalse(FlagDay.newTree(4999, 700), "the earlier of the two limits wins");
	}

	@Test
	void aShortWalkLeavesTheHuntDay() {
		// No walk: 3000 away plus the 300-tick margin ends at 9000, so the last start is 5700.
		assertTrue(FlagDay.newHunt(5700, 0, 0, 0, 1));
		assertFalse(FlagDay.newHunt(5701, 0, 0, 0, 1));
		assertTrue(FlagDay.newHunt(1000, 1, 1, 0, 1), "ten ticks each way still fits a morning start");
		assertFalse(FlagDay.newHunt(1000, 0, 0, 0, 0));
		assertFalse(FlagDay.newHunt(999, 0, 0, 0, 1));
	}

	@Test
	void aHuntCountsTheWalkThereAndBack() {
		// 5700 + 3000 away + 300 margin = 9000. One block each way is 20 ticks, so 5700 no longer fits.
		assertFalse(FlagDay.newHunt(5700, 1, 1, 0, 1));
		assertTrue(FlagDay.newHunt(5680, 1, 1, 0, 1));
		assertFalse(FlagDay.newHunt(5681, 1, 1, 0, 1));
	}

	@Test
	void aHunterAlreadyOutComesBackWhenTheReturnWouldBeLate() {
		assertFalse(FlagDay.stayWouldBeLate(1000, 10, 0, 1), "the whole trip and a short walk still fit");
		assertTrue(FlagDay.stayWouldBeLate(8000, 10, 0, 1));
		// 8800 + 100 of work left + 100 of walking = 9000, still on time.
		assertFalse(FlagDay.stayWouldBeLate(8800, 10, 2900, 1), "a hundred ticks of work and a hundred of walking still fit");
		// 100 blocks home is 1000 ticks. 8000 + 100 + 1000 = 9100, after the meeting starts.
		assertTrue(FlagDay.stayWouldBeLate(8000, 100, 2900, 1), "staying to finish would make the walk home late");
		assertFalse(FlagDay.stayWouldBeLate(10900, 100, Balance.HUNTER_TRIP_UNITS, 1), "a trip that is already due still comes home");
	}
}
