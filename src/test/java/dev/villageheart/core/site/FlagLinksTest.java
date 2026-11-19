package dev.villageheart.core.site;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.site.FlagLinks.Camp;
import dev.villageheart.core.site.FlagLinks.Flag;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Nearest free camp, a living link stays, and the flag registered first wins a tie. */
class FlagLinksTest {
	private static Flag waiting(int x, int order) { return new Flag(new Pos(x, 64, 0), null, order); }

	private static Flag linked(int x, int campX, int order) { return new Flag(new Pos(x, 64, 0), new Pos(campX, 64, 0), order); }

	@Test
	void theFirstFlagTakesTheNearestFreeCamp() {
		Map<Pos, Pos> links = FlagLinks.assign(List.of(waiting(10, 0)), List.of(new Camp(new Pos(0, 64, 0)), new Camp(new Pos(100, 64, 0))));
		assertEquals(new Pos(0, 64, 0), links.get(new Pos(10, 64, 0)));
	}

	@Test
	void aLivingLinkIsNotMoved() {
		Map<Pos, Pos> links = FlagLinks.assign(
				List.of(linked(10, 100, 0)),
				List.of(new Camp(new Pos(0, 64, 0)), new Camp(new Pos(100, 64, 0))));
		assertEquals(new Pos(100, 64, 0), links.get(new Pos(10, 64, 0)), "the nearer free camp does not steal a living link");
	}

	@Test
	void aLinkWhoseCampIsGoneWaitsAgain() {
		Map<Pos, Pos> links = FlagLinks.assign(List.of(linked(10, 100, 0)), List.of(new Camp(new Pos(0, 64, 0))));
		assertEquals(new Pos(0, 64, 0), links.get(new Pos(10, 64, 0)));
	}

	@Test
	void theNearestWaitingFlagTakesAFreeCamp() {
		Flag farFirst = waiting(90, 0);
		Flag nearSecond = waiting(10, 1);
		Map<Pos, Pos> links = FlagLinks.assign(List.of(farFirst, nearSecond), List.of(new Camp(new Pos(0, 64, 0))));
		assertEquals(new Pos(0, 64, 0), links.get(nearSecond.at()));
		assertFalse(links.containsKey(farFirst.at()), "the farther flag waits");
	}

	@Test
	void theSameDistanceGoesToTheFlagRegisteredFirst() {
		Flag first = waiting(10, 0);
		Flag second = waiting(-10, 1);
		Map<Pos, Pos> links = FlagLinks.assign(List.of(second, first), List.of(new Camp(new Pos(0, 64, 0))));
		assertEquals(new Pos(0, 64, 0), links.get(first.at()));
		assertFalse(links.containsKey(second.at()));
	}

	@Test
	void aFlagWithNoFreeCampWaitsUntilOneIsFree() {
		Flag held = linked(5, 0, 0);
		Flag waiting = waiting(40, 1);
		List<Camp> camps = List.of(new Camp(new Pos(0, 64, 0)));
		assertFalse(FlagLinks.assign(List.of(held, waiting), camps).containsKey(waiting.at()));
		Map<Pos, Pos> freed = FlagLinks.assign(List.of(waiting), camps);
		assertEquals(new Pos(0, 64, 0), freed.get(waiting.at()), "the camp is free once its flag is gone");
	}

	@Test
	void twoCampsGoToTheTwoNearestFlagsAndAThirdWaits() {
		Flag a = waiting(0, 0);
		Flag b = waiting(100, 1);
		Flag c = waiting(50, 2);
		Map<Pos, Pos> links = FlagLinks.assign(List.of(a, b, c),
				List.of(new Camp(new Pos(1, 64, 0)), new Camp(new Pos(99, 64, 0))));
		assertEquals(new Pos(1, 64, 0), links.get(a.at()));
		assertEquals(new Pos(99, 64, 0), links.get(b.at()));
		assertFalse(links.containsKey(c.at()));
	}
}
