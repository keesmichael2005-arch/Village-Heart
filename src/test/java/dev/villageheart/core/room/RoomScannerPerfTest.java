package dev.villageheart.core.room;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** T-L1-16 and T-L1-17: the scanner is linear and fast. The grid is built outside the timed region. */
class RoomScannerPerfTest {
	private static final Cell STONE = Cell.solid("minecraft:stone");

	private static long medianNanos(FakeGrid g, Pos marker, int warmup, int runs, long[] all) {
		for (int i = 0; i < warmup; i++) RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		for (int i = 0; i < runs; i++) {
			long t = System.nanoTime();
			RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
			all[i] = System.nanoTime() - t;
		}
		long[] sorted = all.clone();
		Arrays.sort(sorted);
		return sorted[runs / 2];
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

		long[] all = new long[30];
		long median = medianNanos(g, marker, 50, 30, all);
		assertTrue(median < 5_000_000L, "median " + median / 1000 + " us; all (us): "
				+ Arrays.stream(all).map(n -> n / 1000).boxed().toList());
	}

	/** T-L1-17 */
	@Test
	void capWorstCaseIsBounded() {
		FakeGrid g = new FakeGrid().hollow(0, 0, 0, 21, 21, 21, STONE).door(8, 1, 0);
		Pos marker = new Pos(8, 3, -1);
		RoomScan s = RoomScanner.scanFromMarker(g, marker, Dir.NORTH);
		assertEquals(4097, s.volume());
		assertEquals(RoomProblem.Kind.TOO_BIG, s.problems().get(0).kind());
		long[] all = new long[30];
		long median = medianNanos(g, marker, 50, 30, all);
		assertTrue(median < 10_000_000L, "median " + median / 1000 + " us; all (us): "
				+ Arrays.stream(all).map(n -> n / 1000).boxed().toList());
	}
}
