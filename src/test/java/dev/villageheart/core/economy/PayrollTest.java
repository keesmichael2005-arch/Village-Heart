package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollTest {
	private static UUID id(int n) { return new UUID(0, 5000 - n); }

	private static Payee p(int n, int wage) { return new Payee(id(n), wage); }

	@Test void afterDawnReplacesBothCollections() {
		Payroll yesterday = new Payroll(4, Set.of(id(1)), List.of(p(2, 1)));
		PayResult r = PayRun.run(5, 1, List.of(p(1, 1), p(2, 1)));
		Payroll today = yesterday.afterDawn(r);
		assertEquals(5, today.lastWageDay());
		assertEquals(Set.of(id(1)), today.paidToday());
		assertEquals(List.of(p(2, 1)), today.unpaid());
		// yesterday's striker is today's striker only because it was short again; with money it would simply be paid, nothing is owed
		Payroll rich = yesterday.afterDawn(PayRun.run(5, 9, List.of(p(1, 1), p(2, 1))));
		assertTrue(rich.unpaid().isEmpty());
		assertEquals(Set.of(id(1), id(2)), rich.paidToday());
	}

	@Test void settlePaysOnlyStrikersAtTheirStoredWageInHiringOrder() {
		Payroll pr = new Payroll(1, Set.of(id(9)), List.of(p(1, 2), p(2, 1), p(3, 1)));
		Settlement s = pr.settle(2);
		assertEquals(List.of(id(1)), s.paid(), "the first striker takes both emeralds");
		assertEquals(0, s.treasuryAfter());
		assertEquals(List.of(p(2, 1), p(3, 1)), s.payroll().unpaid());
		assertEquals(Set.of(id(9), id(1)), s.payroll().paidToday());
		assertEquals(Set.of(id(9)), pr.paidToday(), "the old payroll is untouched");
	}

	@Test void settleSkipsOnPastAWorkerThatDoesNotFit() {
		Payroll pr = new Payroll(1, Set.of(), List.of(p(1, 2), p(2, 1)));
		Settlement s = pr.settle(1);
		assertEquals(List.of(id(2)), s.paid());
		assertEquals(List.of(p(1, 2)), s.payroll().unpaid());
	}

	@Test void settleTwiceNeverPaysAnyoneTwice() {
		Payroll pr = new Payroll(1, Set.of(), List.of(p(1, 1), p(2, 1)));
		Settlement a = pr.settle(1);
		Settlement b = a.payroll().settle(a.treasuryAfter() + 1);
		assertEquals(List.of(id(1)), a.paid());
		assertEquals(List.of(id(2)), b.paid());
		assertEquals(0, b.treasuryAfter());
		Settlement c = b.payroll().settle(10);
		assertTrue(c.paid().isEmpty());
		assertEquals(10, c.treasuryAfter());
	}

	@Test void aHireOfSomeoneAlreadyPaidTodayCostsNothing() {
		Payroll pr = new Payroll(1, Set.of(id(1)), List.of());
		Settlement s = pr.hire(0, p(1, 1));
		assertEquals(0, s.treasuryAfter());
		assertTrue(s.paid().isEmpty());
		assertEquals(pr, s.payroll());
	}

	@Test void aNewHireIsChargedIfItFitsElseItStrikes() {
		Payroll pr = Payroll.initial(3);
		Settlement paid = pr.hire(5, p(1, 2));
		assertEquals(3, paid.treasuryAfter());
		assertTrue(paid.payroll().paidToday().contains(id(1)));
		Settlement striking = pr.hire(1, p(1, 2));
		assertEquals(1, striking.treasuryAfter());
		assertTrue(striking.payroll().isUnpaid(id(1)));
		assertEquals(List.of(p(1, 2)), striking.payroll().unpaid());
	}

	@Test void aStrikerHiredAgainStaysAStrikerAndIsNotChargedTwice() {
		Payroll pr = new Payroll(3, Set.of(), List.of(p(1, 1)));
		Settlement s = pr.hire(9, p(1, 1));
		assertEquals(9, s.treasuryAfter());
		assertEquals(pr, s.payroll());
	}

	@Test void fireAtDuskAndRehireAfterDawnGivesNoFreeDay() {
		// day 5: both workers were paid, are fired at 23 999 and the dawn of day 6 runs with nobody on the books
		Payroll day5 = new Payroll(5, Set.of(id(1), id(2)), List.of());
		Payroll day6 = day5.afterDawn(PayRun.run(6, 0, List.of()));
		assertTrue(day6.paidToday().isEmpty());
		Settlement a = day6.hire(0, p(1, 1));
		Settlement b = a.payroll().hire(a.treasuryAfter(), p(2, 1));
		assertTrue(b.payroll().isUnpaid(id(1)) && b.payroll().isUnpaid(id(2)), "no money, no free day: both strike from their first tick");
		Settlement rich = day6.hire(2, p(1, 1));
		Settlement rich2 = rich.payroll().hire(rich.treasuryAfter(), p(2, 1));
		assertEquals(0, rich2.treasuryAfter(), "with money each rehire is charged");
	}

	@Test void rotatingTwoVillagersAcrossThreeDawnsStillPaysEveryDay() {
		Payroll pr = Payroll.initial(0);
		int treasury = 0;
		UUID[] who = {id(1), id(2)};
		for (int day = 1; day <= 3; day++) {
			pr = pr.afterDawn(PayRun.run(day, treasury, List.of())); // the worker was fired before the dawn
			Settlement s = pr.hire(treasury, new Payee(who[day % 2], 1)); // the other one is hired after it
			pr = s.payroll();
			treasury = s.treasuryAfter();
			assertTrue(pr.isUnpaid(who[day % 2]), "day " + day + ": the hired worker works only once paid");
		}
	}

	@Test void consumeDawnEmptiesBothAndMovesTheDay() {
		Payroll pr = new Payroll(2, Set.of(id(1)), List.of(p(2, 1)));
		Payroll c = pr.consumeDawn(9);
		assertEquals(9, c.lastWageDay());
		assertTrue(c.paidToday().isEmpty() && c.unpaid().isEmpty());
	}

	@Test void isUnpaidIsFalseForAStranger() {
		assertFalse(new Payroll(1, Set.of(), List.of(p(1, 1))).isUnpaid(id(2)));
	}

	@Test void strikersCountsOnlyCurrentWorkers() {
		Payroll pr = new Payroll(1, Set.of(), List.of(p(1, 1), p(2, 1)));
		assertEquals(1, pr.strikers(Set.of(id(2), id(3))));
		assertEquals(0, pr.strikers(Set.of()));
	}

	@Test void duplicatesAndOverlapAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Payroll(1, Set.of(), List.of(p(1, 1), p(1, 2))));
		assertThrows(IllegalArgumentException.class, () -> new Payroll(1, Set.of(id(1)), List.of(p(1, 1))));
	}
}
