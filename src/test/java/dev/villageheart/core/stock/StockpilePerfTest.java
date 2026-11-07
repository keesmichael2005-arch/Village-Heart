package dev.villageheart.core.stock;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Wall-clock limits (M6 budget). Tagged "perf": run by {@code ./gradlew perfTest}; the call-count guard is {@link StockpileCostTest}. */
@Tag("perf")
class StockpilePerfTest {
	private static List<FakeInventory> invs;

	/** 64 sites x 8 containers x 27 slots; {@code fillPercent} of the slots hold one of 300 items. */
	private static Stockpile<String> grid(int fillPercent) {
		List<StorageSite<String>> list = new ArrayList<>();
		invs = new ArrayList<>();
		int n = 0;
		for (int s = 0; s < 64; s++) {
			List<ContainerRef<String>> cs = new ArrayList<>();
			for (int c = 0; c < 8; c++) {
				FakeInventory inv = new FakeInventory();
				for (int i = 0; i < 27; i++, n++) if (n % 100 < fillPercent) inv.set(i, "item" + (n % 300), 1 + n % 60);
				invs.add(inv);
				cs.add(new ContainerRef<>(new Pos(s, 0, c), inv));
			}
			list.add(new StorageSite<>(new Pos(s, 0, 0), new Pos(s * 3, 0, 0), cs));
		}
		return new Stockpile<>(list);
	}

	private static final int MAX_BATCHES = 50;
	private static final long BUDGET_NANOS = 5_000_000_000L;

	/**
	 * Batches of 30 timed runs (median each) with a 100 ms pause between them, until one batch median is under {@code limit} or the
	 * budget (50 batches / 5 s) runs out, as in RoomScannerPerfTest. Contention only adds time, so stopping at the first good batch is
	 * sound; a real regression never gets under the limit. Returns the best batch median.
	 */
	private static long bestBatchMedian(Runnable r, Runnable reset, long limit, List<Long> batchMedians) throws InterruptedException {
		for (int i = 0; i < 30; i++) {
			reset.run();
			r.run();
		}
		long start = System.nanoTime();
		long best = Long.MAX_VALUE;
		while (batchMedians.size() < MAX_BATCHES && System.nanoTime() - start < BUDGET_NANOS) {
			long[] t = new long[30];
			for (int i = 0; i < t.length; i++) {
				reset.run();
				long a = System.nanoTime();
				r.run();
				t[i] = System.nanoTime() - a;
			}
			Arrays.sort(t);
			batchMedians.add(t[t.length / 2]);
			best = Math.min(best, t[t.length / 2]);
			if (best < limit) break;
			Thread.sleep(100);
		}
		return best;
	}

	private static String us(List<Long> nanos) { return nanos.stream().map(n -> n / 1000).toList().toString(); }

	@Test void snapshotUnder3ms() throws InterruptedException {
		Stockpile<String> s = grid(90);
		int[] distinct = new int[1];
		List<Long> b = new ArrayList<>();
		long m = bestBatchMedian(() -> distinct[0] = s.snapshot(0, 0, 0, StorageAlert.OK).totals().size(), () -> {}, 3_000_000L, b);
		System.out.println("perf stockpile snapshot (13824 slots): best batch median " + m / 1000 + " us after " + b.size() + " batch(es)");
		assertEquals(270, distinct[0]); // 300 item ids, those with n%100 >= 90 never occur in a 90% fill
		assertTrue(m < 3_000_000L, "best batch median " + m / 1000 + " us; all batch medians (us): " + us(b));
	}

	@Test void fullStorageDepositUnder1ms() throws InterruptedException {
		Stockpile<String> s = grid(100);
		for (FakeInventory f : invs) for (int i = 0; i < 27; i++) f.set(i, "full" + i, 64);
		int[] stored = {-1};
		List<Long> b = new ArrayList<>();
		long m = bestBatchMedian(() -> stored[0] = s.deposit(new Pos(0, 0, 0), "cobble", 10), () -> {}, 1_000_000L, b);
		System.out.println("perf stockpile full-storage deposit: best batch median " + m / 1000 + " us after " + b.size() + " batch(es)");
		assertEquals(0, stored[0]);
		assertTrue(m < 1_000_000L, "best batch median " + m / 1000 + " us; all batch medians (us): " + us(b));
	}

	@Test void spillAcrossAllSitesUnder3ms() throws InterruptedException {
		Stockpile<String> st = grid(0);
		List<FakeInventory> all = invs;
		int want = 64 * 27 * 8 * 64;
		int[] stored = {-1};
		Runnable reset = () -> all.forEach(f -> {
			Arrays.fill(f.keys, null);
			Arrays.fill(f.counts, 0);
		});
		List<Long> b = new ArrayList<>();
		long m = bestBatchMedian(() -> stored[0] = st.deposit(new Pos(0, 0, 0), "cobble", want), reset, 3_000_000L, b);
		System.out.println("perf stockpile spill across 64 sites: best batch median " + m / 1000 + " us after " + b.size() + " batch(es)");
		assertEquals(want, stored[0]);
		assertTrue(m < 3_000_000L, "best batch median " + m / 1000 + " us; all batch medians (us): " + us(b));
	}
}
