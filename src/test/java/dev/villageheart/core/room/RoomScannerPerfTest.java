package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-L1-16 and T-L1-17 wall-clock limits. Tagged "perf": excluded from {@code test}, run by {@code ./gradlew perfTest}
 * (verify.sh runs it alone, after the other Gradle steps). The call-count guards live in {@link RoomScannerCostTest}.
 */
@Tag("perf")
class RoomScannerPerfTest {
	private static final Cell STONE = Cell.solid("minecraft:stone");
	private static final int MAX_BATCHES = 50;
	private static final long BUDGET_NANOS = 5_000_000_000L;

	/**
	 * Batches of 30 timed scans (median each) with a pause between them, until one batch median is under {@code limit} or the
	 * budget (50 batches / 5 s) runs out. Contention only adds time, so stopping at the first good batch is sound; a real
	 * regression never gets under the limit. Returns the best batch median.
	 */
	private static long bestBatchMedianNanos(FakeGrid g, Pos marker, long limit, List<Long> batchMedians) throws InterruptedException {
		for (int i = 0; i < 50; i++) RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		long start = System.nanoTime();
		long best = Long.MAX_VALUE;
		while (batchMedians.size() < MAX_BATCHES && System.nanoTime() - start < BUDGET_NANOS) {
			long[] all = new long[30];
			for (int i = 0; i < all.length; i++) {
				long t = System.nanoTime();
				RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
				all[i] = System.nanoTime() - t;
			}
			Arrays.sort(all);
			batchMedians.add(all[all.length / 2]);
			best = Math.min(best, all[all.length / 2]);
			if (best < limit) break;
			Thread.sleep(100);
		}
		return best;
	}

	private static String us(List<Long> nanos) { return nanos.stream().map(n -> n / 1000).toList().toString(); }

	/** T-L1-16 (roadmap) */
	@Test
	void room16x16x8Under5ms() throws InterruptedException {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 17, 9, 17, STONE).door(8, 1, 0);
		for (int i = 0; i < 4; i++) g.set(2 + i, 1, 16, Cell.solid("minecraft:chest", "#chest"));
		for (int i = 0; i < 2; i++) {
			g.set(2 + 2 * i, 1, 10, Cell.SOLID.withPartner(Dir.SOUTH));
			g.set(2 + 2 * i, 1, 11, Cell.solid("minecraft:red_bed", "#bed"));
		}
		Pos marker = new Pos(8, 3, -1);
		RoomScan s = RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		assertTrue(s.valid(), s.problems().toString());
		assertEquals(2048 - 4 - 4, s.volume());
		assertEquals(256, s.floorArea());
		assertEquals(4, s.count("#chest"));
		assertEquals(2, s.count("#bed"));

		List<Long> batches = new ArrayList<>();
		long best = bestBatchMedianNanos(g, marker, 5_000_000L, batches);
		System.out.println("perf 16x16x8: best batch median " + best / 1000 + " us after " + batches.size() + " batch(es)");
		assertTrue(best < 5_000_000L, "best batch median " + best / 1000 + " us; all batch medians (us): " + us(batches));
	}

	/** T-L1-17: wall-clock side (the call-count side is RoomScannerCostTest). */
	@Test
	void capWorstCaseIsFast() throws InterruptedException {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 21, 21, 21, STONE).door(8, 1, 0);
		Pos marker = new Pos(8, 3, -1);
		assertEquals(4097, RoomScanner.scanFromMarker(g, marker, Dir.NORTH).volume());
		List<Long> batches = new ArrayList<>();
		long best = bestBatchMedianNanos(g, marker, 10_000_000L, batches);
		System.out.println("perf cap worst case: best batch median " + best / 1000 + " us after " + batches.size() + " batch(es)");
		assertTrue(best < 10_000_000L, "best batch median " + best / 1000 + " us; all batch medians (us): " + us(batches));
	}
}
