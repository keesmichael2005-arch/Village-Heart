package dev.villageheart.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.sim.TickTimer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Wall-clock limit moved out of the GameTest villageTickerTimesSystems (M4 rule). Run by {@code ./gradlew perfTest}. */
@Tag("perf")
class VillageTickerPerfTest {
	@Test void idleTickWithNoSystemsIsCheap() throws InterruptedException {
		double best = Double.MAX_VALUE;
		for (int batch = 0; batch < 20 && best >= 0.5; batch++) {
			TickTimer timer = new TickTimer();
			for (int i = 0; i < 200; i++) {
				long start = System.nanoTime();
				VillageSystems.tickAll(null); // no registered systems: returns before touching the server
				timer.add(System.nanoTime() - start);
				timer.commitTick();
			}
			best = Math.min(best, timer.snapshot().meanMs());
			if (best >= 0.5) Thread.sleep(50);
		}
		System.out.println("perf village ticker idle tick: best batch mean " + best + " ms");
		assertTrue(best < 0.5, "idle tick with no systems must cost under 0.5 ms, best batch mean " + best);
	}
}
