package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Roadmap M12 L1: paid in hiring order when the treasury is short. */
class PayRunTest {
	/** UUIDs whose natural order is the reverse of the order they are listed in, so a sort by UUID would pick the wrong strikers. */
	private static UUID id(int n) { return new UUID(0, 1000 - n); }

	private static Payee p(int n, int wage) { return new Payee(id(n), wage); }

	@Test void threeLevelOneWorkersTwoEmeralds() {
		PayResult r = PayRun.run(7, 2, List.of(p(1, 1), p(2, 1), p(3, 1)));
		assertEquals(List.of(id(1), id(2)), r.paid(), "the first two in hiring order are paid");
		assertEquals(List.of(p(3, 1)), r.unpaid());
		assertEquals(2, r.treasuryBefore());
		assertEquals(0, r.treasuryAfter());
		assertEquals(2, r.spent());
		assertEquals(3, r.bill());
		assertEquals(7, r.day());
		assertTrue(id(1).compareTo(id(3)) > 0, "the test ids are in reverse natural order");
	}

	@Test void enoughForEveryone() {
		PayResult r = PayRun.run(1, 10, List.of(p(1, 1), p(2, 2), p(3, 1)));
		assertEquals(3, r.paid().size());
		assertTrue(r.unpaid().isEmpty());
		assertEquals(6, r.treasuryAfter());
		assertEquals(4, r.spent());
	}

	@Test void emptyTreasuryPaysNobody() {
		PayResult r = PayRun.run(1, 0, List.of(p(1, 1), p(2, 1)));
		assertTrue(r.paid().isEmpty());
		assertEquals(2, r.unpaid().size());
		assertEquals(0, r.spent());
		assertEquals(0, r.treasuryAfter());
	}

	@Test void aCheaperLaterWorkerIsStillPaid() {
		PayResult r = PayRun.run(1, 1, List.of(p(1, 2), p(2, 1)));
		assertEquals(List.of(id(2)), r.paid(), "the level 3 worker strikes, the level 1 worker behind it is paid");
		assertEquals(List.of(p(1, 2)), r.unpaid());
	}

	@Test void skipOnKeepsThePriorityOrder() {
		PayResult r = PayRun.run(1, 2, List.of(p(1, 1), p(2, 2), p(3, 1)));
		assertEquals(List.of(id(1), id(3)), r.paid());
		assertEquals(List.of(p(2, 2)), r.unpaid());
		assertEquals(0, r.treasuryAfter());
	}

	@Test void noPayees() {
		PayResult r = PayRun.run(4, 5, List.of());
		assertTrue(r.paid().isEmpty() && r.unpaid().isEmpty());
		assertEquals(5, r.treasuryAfter());
		assertEquals(0, r.bill());
	}

	@Test void negativeTreasuryIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> PayRun.run(1, -1, List.of()));
	}

	@Test void noneIsADawnWithoutMoney() {
		PayResult r = PayResult.none(3, 9);
		assertEquals(9, r.treasuryBefore());
		assertEquals(9, r.treasuryAfter());
		assertTrue(r.paid().isEmpty() && r.unpaid().isEmpty());
	}

	@Test void randomCasesKeepTheInvariants() {
		Random rnd = new Random(12);
		for (int c = 0; c < 1000; c++) {
			int n = rnd.nextInt(8);
			int treasury = rnd.nextInt(12);
			List<Payee> in = new ArrayList<>();
			for (int i = 0; i < n; i++) in.add(p(i, 1 + rnd.nextInt(3)));
			PayResult r = PayRun.run(c, treasury, in);
			assertTrue(r.treasuryAfter() >= 0);
			int paidWages = 0;
			Set<UUID> paid = new HashSet<>(r.paid());
			for (Payee x : in) if (paid.contains(x.villager())) paidWages += x.wage();
			assertEquals(paidWages, r.spent());
			assertEquals(treasury - r.treasuryAfter(), r.spent());
			// every unpaid worker did not fit when it was reached
			int left = treasury;
			List<UUID> order = new ArrayList<>();
			for (Payee x : in) {
				if (paid.contains(x.villager())) {
					left -= x.wage();
					order.add(x.villager());
				} else {
					assertTrue(x.wage() > left, "an unpaid worker's wage must exceed what was left when it was reached");
				}
			}
			assertEquals(order, r.paid(), "paid keeps hiring order");
			List<UUID> all = new ArrayList<>(r.paid());
			r.unpaid().forEach(u -> all.add(u.villager()));
			assertEquals(new HashSet<>(in.stream().map(Payee::villager).toList()), new HashSet<>(all));
			assertEquals(n, all.size());
			assertEquals(in.stream().mapToInt(Payee::wage).sum(), r.bill());
			List<Payee> expectedUnpaid = in.stream().filter(x -> !paid.contains(x.villager())).toList();
			assertEquals(expectedUnpaid, r.unpaid(), "unpaid keeps hiring order");
		}
	}
}
