package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** T-L1-16 and T-L1-17: the scanner is linear and fast. The grid is built outside the timed region. */
class RoomScannerPerfTest {
	private static final Cell STONE = Cell.solid("minecraft:stone");

	/** Best of 5 batches of 30 timed scans after warm-up: contention only adds time, so the fastest batch measures the code. */
	private static long bestBatchMedianNanos(FakeGrid g, Pos marker, long[] batchMedians) {
		for (int i = 0; i < 50; i++) RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		long best = Long.MAX_VALUE;
		for (int b = 0; b < batchMedians.length; b++) {
			long[] all = new long[30];
			for (int i = 0; i < all.length; i++) {
				long t = System.nanoTime();
				RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
				all[i] = System.nanoTime() - t;
			}
			Arrays.sort(all);
			batchMedians[b] = all[all.length / 2];
			best = Math.min(best, batchMedians[b]);
		}
		return best;
	}

	private static String us(long[] nanos) { return Arrays.stream(nanos).map(n -> n / 1000).boxed().toList().toString(); }

	/** Counts view calls so the cap test does not depend on the wall clock. */
	private static final class CountingView implements BlockView {
		final BlockView inner;
		int cells, sky;
		CountingView(BlockView inner) { this.inner = inner; }
		@Override public Cell cell(int x, int y, int z) { cells++; return inner.cell(x, y, z); }
		@Override public boolean openToSky(int x, int y, int z) { sky++; return inner.openToSky(x, y, z); }
	}

	/** T-L1-16 (roadmap) */
	@Test
	void room16x16x8Under5ms() {
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

		long[] batches = new long[5];
		long best = bestBatchMedianNanos(g, marker, batches);
		System.out.println("perf 16x16x8: best batch median " + best / 1000 + " us");
		assertTrue(best < 5_000_000L, "best batch median " + best / 1000 + " us; batch medians (us): " + us(batches));
	}

	/** T-L1-17 */
	@Test
	void capWorstCaseIsBounded() {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 21, 21, 21, STONE).door(8, 1, 0);
		Pos marker = new Pos(8, 3, -1);
		RoomScan s = RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		assertEquals(4097, s.volume());
		assertEquals(RoomProblem.Kind.TOO_BIG, s.problems().get(0).kind());
		CountingView counting = new CountingView(g);
		RoomScan counted = RoomScanner.scanFromMarker(counting, marker, Dir.NORTH);
		assertEquals(4097, counted.volume());
		assertTrue(counting.cells <= 8 * 4097, "cell() calls " + counting.cells);
		assertTrue(counting.sky <= 4097 + 16, "openToSky() calls " + counting.sky);

		long[] batches = new long[5];
		long best = bestBatchMedianNanos(g, marker, batches);
		System.out.println("perf cap worst case: best batch median " + best / 1000 + " us");
		assertTrue(best < 10_000_000L, "best batch median " + best / 1000 + " us; batch medians (us): " + us(batches));
	}
}
