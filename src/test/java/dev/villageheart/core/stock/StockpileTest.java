package dev.villageheart.core.stock;

import static dev.villageheart.core.stock.FakeInventory.site;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StockpileTest {
	private static final Pos ORIGIN = new Pos(0, 0, 0);

	private static Stockpile<String> one(FakeInventory inv) { return new Stockpile<>(List.of(site(1, 1, inv))); }

	// ---- the three ported M0 cases (T-L1-1..3) ---------------------------------------------------

	@Test void depositAndWithdraw() {
		Stockpile<String> s = one(new FakeInventory());
		assertEquals(10, s.deposit(ORIGIN, "oak_log", 10));
		assertEquals(4, s.withdraw(ORIGIN, "oak_log", 4, null));
		assertEquals(6, s.count("oak_log"));
	}

	@Test void cannotWithdrawMoreThanStored() {
		FakeInventory inv = new FakeInventory();
		Stockpile<String> s = one(inv);
		s.deposit(ORIGIN, "bread", 2);
		assertEquals(2, s.withdraw(ORIGIN, "bread", 5, null));
		assertEquals(0, s.count("bread"));
		assertTrue(s.snapshot(0, 0, 0, StorageAlert.OK).totals().isEmpty());
		assertNull(inv.key(0), "no 0-count slot is left behind");
	}

	@Test void rejectsNegative() {
		FakeInventory inv = new FakeInventory();
		Stockpile<String> s = one(inv);
		assertThrows(IllegalArgumentException.class, () -> s.deposit(ORIGIN, "x", -1));
		assertThrows(IllegalArgumentException.class, () -> s.withdraw(ORIGIN, "x", -1, null));
		assertEquals(0, s.deposit(ORIGIN, "x", 0));
		assertEquals(0, s.withdraw(ORIGIN, "x", 0, null));
		assertEquals(0, inv.changes, "a no-op does not touch the chest");
	}

	// ---- aggregation -----------------------------------------------------------------------------

	@Test void aggregatesAcrossSitesAndContainers() {
		FakeInventory a1 = new FakeInventory().set(0, "oak_log", 10).set(1, "bread", 5);
		FakeInventory a2 = new FakeInventory().set(0, "oak_log", 20);
		FakeInventory b1 = new FakeInventory().set(0, "oak_log", 7).set(1, "sword", 1).set(2, "sword", 1);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 1, a1, a2), site(2, 2, b1)));
		assertEquals(37, s.count("oak_log"));
		assertEquals(2, s.count("sword"));
		assertEquals(37 + 2, s.count(k -> k.startsWith("oak") || k.equals("sword")));
		StockSnapshot<String> snap = s.snapshot(5, 0, 0, StorageAlert.OK);
		assertEquals(List.of(new StockSnapshot.Total<>("oak_log", 37L), new StockSnapshot.Total<>("bread", 5L),
				new StockSnapshot.Total<>("sword", 2L)), snap.totals());
		assertEquals(2, snap.sites());
		assertEquals(3, snap.containers());
		assertEquals(81, snap.slots());
		assertEquals(81 - 6, snap.freeSlots());
		assertEquals(5, snap.count("bread"));
	}

	@Test void snapshotTiesKeepFirstSeenOrder() {
		FakeInventory a = new FakeInventory().set(0, "b", 3).set(1, "a", 3).set(2, "c", 3);
		List<StockSnapshot.Total<String>> t = one(a).snapshot(0, 0, 0, StorageAlert.OK).totals();
		assertEquals(List.of("b", "a", "c"), t.stream().map(StockSnapshot.Total::key).toList());
	}

	@Test void snapshotDeterministic() {
		FakeInventory x1 = new FakeInventory().set(0, "a", 5), y1 = new FakeInventory().set(0, "b", 5);
		FakeInventory x2 = new FakeInventory().set(0, "a", 5), y2 = new FakeInventory().set(0, "b", 5);
		StockSnapshot<String> one = new Stockpile<>(List.of(site(1, 1, x1), site(2, 2, y1))).snapshot(9, 0, 0, StorageAlert.OK);
		StockSnapshot<String> two = new Stockpile<>(List.of(site(2, 2, y2), site(1, 1, x2))).snapshot(9, 0, 0, StorageAlert.OK);
		assertEquals(one, two);
	}

	// ---- deposit routing -------------------------------------------------------------------------

	@Test void fillOrderPartialsThenEmpties() {
		FakeInventory first = new FakeInventory().set(0, "dirt", 5);
		FakeInventory second = new FakeInventory().set(3, "cobble", 60);
		FakeInventory third = new FakeInventory();
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 1, first, second, third)));
		assertEquals(10, s.deposit(ORIGIN, "cobble", 10));
		assertEquals(64, second.counts[3]);
		assertEquals("cobble", first.keys[1]);
		assertEquals(6, first.counts[1]);
		assertEquals("dirt", first.keys[0]);
		assertEquals(5, first.counts[0]);
		assertEquals(0, third.total("cobble"));
		assertEquals(1, first.changes);
		assertEquals(1, second.changes);
		assertEquals(0, third.changes);
	}

	@Test void nearestWithSpaceFirst() {
		FakeInventory a = new FakeInventory(), b = new FakeInventory(), c = new FakeInventory();
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 10, a), site(2, 3, b), site(3, 3, c)));
		assertEquals(5, s.deposit(ORIGIN, "x", 5));
		assertEquals(5, b.total("x"));
		assertEquals(0, a.total("x") + c.total("x"));
		assertEquals(new Pos(2, 0, 0), s.nearestWithSpace(ORIGIN, "x").orElseThrow().marker());
	}

	@Test void spillsToNextNearest() {
		FakeInventory near = new FakeInventory().full("dirt").set(0, "cobble", 30).set(1, null, 0);
		FakeInventory far = new FakeInventory();
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 50, far), site(2, 3, near)));
		assertEquals(100, s.deposit(ORIGIN, "cobble", 100));
		assertEquals(30 + 34 + 64, near.total("cobble"));
		assertEquals(64, near.counts[0]);
		assertEquals(64, near.counts[1]);
		assertEquals(2, far.total("cobble"));
		assertEquals(100 + 30, near.total("cobble") + far.total("cobble"));
	}

	@Test void overflowReturnsRemainder() {
		FakeInventory a = new FakeInventory().full("dirt");
		FakeInventory b = new FakeInventory().full("dirt").set(5, "cobble", 60);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 1, a), site(2, 2, b)));
		assertEquals(4, s.deposit(ORIGIN, "cobble", 100));
		assertEquals(64, b.counts[5]);
		assertEquals(27 * 64, a.total("dirt"));
		assertEquals(26 * 64, b.total("dirt"));
		assertFalse(s.hasSpaceFor("cobble"));
		assertFalse(s.hasSpaceFor("dirt"));
		assertEquals(0, s.deposit(ORIGIN, "cobble", 5));
	}

	@Test void limitsRespected() {
		FakeInventory inv = new FakeInventory(27, Set.of("lava"));
		inv.set(10, "cobble", 70); // over-full: has no space and is never shrunk
		Stockpile<String> s = one(inv);
		assertEquals(3, s.deposit(ORIGIN, "sword", 3));
		assertEquals(3, inv.total("sword"));
		assertEquals(20, s.deposit(ORIGIN, "egg", 20));
		assertEquals(16, inv.counts[3]);
		assertEquals(4, inv.counts[4]);
		assertEquals(0, s.deposit(ORIGIN, "lava", 1));
		assertEquals(5, s.deposit(ORIGIN, "cobble", 5));
		assertEquals(70, inv.counts[10]);
		assertEquals(70 + 5, s.count("cobble"));
	}

	@Test void overFullSlotIsNotSpace() {
		FakeInventory inv = new FakeInventory().full("dirt").set(0, "cobble", 70);
		Stockpile<String> s = one(inv);
		assertFalse(s.hasSpaceFor("cobble"));
		assertEquals(0, s.deposit(ORIGIN, "cobble", 5));
		assertEquals(70, inv.counts[0]);
	}

	@Test void depositDoesNotMergeDifferentKeys() {
		FakeInventory inv = new FakeInventory().set(0, "paper{name=Note}", 16);
		Stockpile<String> s = one(inv);
		assertEquals(10, s.deposit(ORIGIN, "paper", 10));
		assertEquals(16, inv.counts[0]);
		StockSnapshot<String> snap = s.snapshot(0, 0, 0, StorageAlert.OK);
		assertEquals(2, snap.totals().size());
		assertEquals(java.util.Map.of("paper", 26L), snap.totalsBy(k -> k.split("\\{")[0]));
	}

	@Test void depositIntoOneSiteOnly() {
		FakeInventory a = new FakeInventory(), b = new FakeInventory().full("dirt").set(0, "cobble", 60);
		StorageSite<String> siteB = site(2, 2, b);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 1, a), siteB));
		assertEquals(4, s.depositInto(s.sites().get(1), "cobble", 100));
		assertEquals(0, a.total("cobble"));
		assertEquals(0, a.changes);
	}

	// ---- withdraw routing ------------------------------------------------------------------------

	@Test void withdrawFromAny() {
		FakeInventory near = new FakeInventory(), far = new FakeInventory().set(0, "log", 30);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 100, far), site(2, 1, near)));
		assertEquals(10, s.withdraw(ORIGIN, "log", 10, null));
		assertEquals(20, far.total("log"));

		near.set(0, "log", 5);
		assertEquals(8, s.withdraw(ORIGIN, "log", 8, null));
		assertEquals(0, near.total("log"), "the near site empties first");
		assertEquals(17, far.total("log"));

		near.set(0, "log", 5);
		assertEquals(3, s.withdraw(null, "log", 3, null)); // site order: marker 1 = far
		assertEquals(14, far.total("log"));
		assertEquals(5, near.total("log"));
	}

	@Test void withdrawFirstMatching() {
		FakeInventory inv = new FakeInventory().set(0, "stone", 9).set(1, "apple", 4).set(2, "bread", 6);
		Stockpile<String> s = one(inv);
		Optional<Stockpile.Taken<String>> t = s.withdrawFirst(ORIGIN, k -> k.startsWith("bread") || k.equals("apple"), 3, null);
		assertEquals(new Stockpile.Taken<>("apple", 3), t.orElseThrow());
		int changes = inv.changes;
		assertTrue(s.withdrawFirst(ORIGIN, k -> k.equals("gold"), 3, null).isEmpty());
		assertEquals(changes, inv.changes);
		assertEquals(1, inv.counts[1]);
	}

	// ---- reservations ----------------------------------------------------------------------------

	@Test void reservationsClampAndAreConsumed() {
		java.util.UUID h1 = new java.util.UUID(0, 1), h2 = new java.util.UUID(0, 2);
		Reservations<String> r = new Reservations<>();
		FakeInventory inv = new FakeInventory().set(0, "plank", 32);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 1, inv)), r);
		r.set(h1, "plank", 30);
		r.set(h2, "plank", 10);
		assertEquals(40, r.reserved("plank"));
		assertEquals(0, s.available("plank", null));
		assertEquals(22, s.available("plank", h1));
		assertEquals(0, s.withdraw(ORIGIN, "plank", 40, null));
		assertEquals(22, s.withdraw(ORIGIN, "plank", 40, h1));
		assertEquals(8, r.reservedBy(h1, "plank"));
		assertEquals(18, r.reserved("plank"));
		r.release(h1);
		assertEquals(10, r.reserved("plank"));
		r.set(h2, "plank", 0);
		assertEquals(0, r.reserved("plank"));
		assertEquals(10, s.available("plank", null));
	}
}
