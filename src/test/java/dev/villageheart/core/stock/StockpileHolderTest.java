package dev.villageheart.core.stock;

import static dev.villageheart.core.stock.FakeInventory.site;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** M13 T-L1-8: the Storage a cook walks to holds something the cook may take, and it takes from that Storage only. */
class StockpileHolderTest {
	private static final Pos ORIGIN = new Pos(0, 0, 0);
	private static final UUID COOK = new UUID(0, 1);
	private static final UUID OTHER = new UUID(0, 2);

	@Test
	void nearestSiteThatHoldsTheKeySkipsNearerSitesWithoutIt() {
		FakeInventory near = new FakeInventory().set(0, "wheat", 5);
		FakeInventory far = new FakeInventory().set(0, "beef", 4);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 2, near), site(2, 30, far)));
		assertEquals(new Pos(30, 0, 0), s.nearestHolding(ORIGIN, "beef", COOK).orElseThrow().anchor());
		assertEquals(new Pos(2, 0, 0), s.nearestHolding(ORIGIN, "wheat", COOK).orElseThrow().anchor());
		assertTrue(s.nearestHolding(ORIGIN, "coal", COOK).isEmpty());
	}

	@Test
	void whatOthersReservedIsNotTheCooks() {
		FakeInventory a = new FakeInventory().set(0, "beef", 4);
		Reservations<String> res = new Reservations<>();
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 2, a)), res);
		res.set(OTHER, "beef", 4);
		assertTrue(s.nearestHolding(ORIGIN, "beef", COOK).isEmpty(), "all of it is reserved by someone else");
		assertTrue(s.nearestHolding(ORIGIN, "beef", OTHER).isPresent(), "the holder's own reservation counts for it");
		res.set(OTHER, "beef", 3);
		assertTrue(s.nearestHolding(ORIGIN, "beef", COOK).isPresent());
	}

	@Test
	void withdrawFromTakesFromThatSiteOnlyAndConsumesTheReservation() {
		FakeInventory a = new FakeInventory().set(0, "beef", 3);
		FakeInventory b = new FakeInventory().set(0, "beef", 5);
		Reservations<String> res = new Reservations<>();
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 2, a), site(2, 30, b)), res);
		res.set(COOK, "beef", 6);
		var far = s.sites().get(1);
		assertEquals(5, s.withdrawFrom(far, "beef", 6, COOK), "only what that site holds");
		assertEquals(3, s.count(k -> k.equals("beef")), "the near site was not touched");
		assertEquals(1, res.reservedBy(COOK, "beef"), "5 of the 6 reserved were consumed");
		assertEquals(0, s.withdrawFrom(far, "beef", 6, COOK), "none left there");
	}

	@Test
	void withdrawFromNeverTakesWhatOthersReserved() {
		FakeInventory a = new FakeInventory().set(0, "beef", 4);
		Reservations<String> res = new Reservations<>();
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 2, a)), res);
		res.set(OTHER, "beef", 3);
		assertEquals(1, s.withdrawFrom(s.sites().get(0), "beef", 4, COOK));
		assertEquals(3, s.count("beef"));
	}
}
