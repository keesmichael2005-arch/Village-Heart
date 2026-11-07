package dev.villageheart.core.stock;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Deterministic cost guard (operation counts, not wall-clock): the wall-clock side is {@link StockpilePerfTest}. */
class StockpileCostTest {
	/** Counts every read of a slot. */
	static final class Counting implements Inventory<String> {
		static long reads;
		final FakeInventory in = new FakeInventory();
		@Override public int size() { return in.size(); }
		@Override public String key(int s) { reads++; return in.key(s); }
		@Override public int count(int s) { return in.count(s); }
		@Override public int limit(int s, String k) { return in.limit(s, k); }
		@Override public void add(int s, String k, int n) { in.add(s, k, n); }
		@Override public void remove(int s, int n) { in.remove(s, n); }
		@Override public void changed() { in.changed(); }
	}

	private static Stockpile<String> grid(int sites, int containers) {
		List<StorageSite<String>> list = new ArrayList<>();
		for (int s = 0; s < sites; s++) {
			List<ContainerRef<String>> cs = new ArrayList<>();
			for (int c = 0; c < containers; c++) cs.add(new ContainerRef<>(new Pos(s, 0, c), new Counting()));
			list.add(new StorageSite<>(new Pos(s, 0, 0), new Pos(s, 0, 0), cs));
		}
		return new Stockpile<>(list);
	}

	@Test void snapshotReadsEachSlotOnce() {
		Stockpile<String> s = grid(8, 8);
		Counting.reads = 0;
		s.snapshot(0, 0, 0, StorageAlert.OK);
		assertEquals(8 * 8 * 27, Counting.reads);
	}

	@Test void spillingDepositIsLinearInSlots() {
		Stockpile<String> s = grid(64, 8);
		int slots = 64 * 8 * 27;
		Counting.reads = 0;
		assertEquals(slots * 64, s.deposit(new Pos(0, 0, 0), "cobble", slots * 64));
		// per site: at most one partial pass and one empty pass over its slots, nothing more
		assertTrue(Counting.reads <= 2L * slots, "reads " + Counting.reads);
	}

	@Test void fullStorageDepositReadsEachSlotAtMostTwice() {
		Stockpile<String> s = grid(16, 8);
		for (StorageSite<String> site : s.sites()) for (ContainerRef<String> c : site.containers()) ((Counting) c.inventory()).in.full("dirt");
		Counting.reads = 0;
		assertEquals(0, s.deposit(new Pos(0, 0, 0), "cobble", 10));
		assertTrue(Counting.reads <= 2L * 16 * 8 * 27, "reads " + Counting.reads);
	}
}
