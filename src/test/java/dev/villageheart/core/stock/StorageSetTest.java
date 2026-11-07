package dev.villageheart.core.stock;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.stock.StorageSet.Resolved;
import dev.villageheart.core.stock.StorageSet.SiteCandidate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StorageSetTest {
	private static Pos p(int x) { return new Pos(x, 0, 0); }

	@Test void excludesOtherBuildingsAndDedupes() {
		SiteCandidate a = new SiteCandidate(p(0), p(0), List.of(p(5), p(2), p(1), p(1)));
		SiteCandidate b = new SiteCandidate(p(100), p(100), List.of(p(9), p(5)));
		Resolved r = StorageSet.resolve(List.of(b, a), Set.of(p(2)), List.of(), 32);
		assertEquals(List.of(p(1), p(5)), r.sites().get(0).containers());
		assertEquals(List.of(p(9)), r.sites().get(1).containers());
		assertTrue(r.unknownMarkers().isEmpty());
	}

	@Test void siteWithEveryPositionExcludedIsKept() {
		SiteCandidate a = new SiteCandidate(p(0), p(0), List.of(p(2)));
		Resolved r = StorageSet.resolve(List.of(a), Set.of(p(2)), List.of(), 32);
		assertEquals(1, r.sites().size());
		assertTrue(r.sites().get(0).containers().isEmpty());
	}

	@Test void unscannedNeighbourMakesStorageUnknown() {
		SiteCandidate a = new SiteCandidate(p(0), p(0), List.of(p(1)));
		Resolved near = StorageSet.resolve(List.of(a), Set.of(), List.of(p(20)), 32);
		assertTrue(near.sites().isEmpty());
		assertEquals(List.of(p(0)), near.unknownMarkers());
		Resolved far = StorageSet.resolve(List.of(a), Set.of(), List.of(p(40)), 32);
		assertEquals(1, far.sites().size());
		assertTrue(far.unknownMarkers().isEmpty());
	}

	@Test void unknownStorageDoesNotOwnPositions() {
		SiteCandidate a = new SiteCandidate(p(0), p(0), List.of(p(5)));
		SiteCandidate b = new SiteCandidate(p(100), p(100), List.of(p(5)));
		Resolved r = StorageSet.resolve(List.of(a, b), Set.of(), List.of(p(10)), 32);
		assertEquals(List.of(p(0)), r.unknownMarkers());
		assertEquals(List.of(p(5)), r.sites().get(0).containers());
	}
}
