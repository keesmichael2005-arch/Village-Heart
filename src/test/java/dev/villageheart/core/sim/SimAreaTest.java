package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.WorkCounters;
import dev.villageheart.core.sim.SimArea.Chunk;
import dev.villageheart.core.sim.SimArea.Follower;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimAreaTest {
	@Test
	void staticSquareCoversTheClaimWhereverTheHeartStandsInItsChunk() {
		int r = SimArea.staticRadiusChunks(Balance.CLAIM_RADIUS);
		assertEquals(6, r, "80-block claim: 5 chunks reach the far edge from any spot in the Heart's chunk, plus one");
		int R = Balance.CLAIM_RADIUS;
		for (int hx = 0; hx < 16; hx++) for (int hz = 0; hz < 16; hz++) {
			int x = 640 + hx, z = 816 + hz;
			Set<Chunk> sq = SimArea.square(Chunk.ofBlock(x, z), r);
			for (int[] d : new int[][] {{R, 0}, {-R, 0}, {0, R}, {0, -R}, {R, R}, {-R, -R}}) {
				assertTrue(sq.contains(Chunk.ofBlock(x + d[0], z + d[1])), "claim edge " + d[0] + "," + d[1] + " from " + x + "," + z);
				assertTrue(sq.contains(Chunk.ofBlock(x + d[0] + Integer.signum(d[0]) * 16, z + d[1] + Integer.signum(d[1]) * 16)),
						"one chunk of margin past the claim edge");
			}
		}
		assertEquals(169, SimArea.square(new Chunk(40, 51), 6).size());
		assertEquals(1, SimArea.staticRadiusChunks(0));
		assertThrows(IllegalArgumentException.class, () -> SimArea.staticRadiusChunks(-1));
	}

	@Test
	void chunkOfNegativeBlocks() {
		assertEquals(new Chunk(-1, -1), Chunk.ofBlock(-1, -16));
		assertEquals(new Chunk(47, 51), Chunk.ofBlock(752, 818), "the r5 farmer froze in chunk 47, one past the old square");
	}

	@Test
	void followTicketTracksAWorkerChunkByChunk() {
		Follower<String> f = new Follower<>();
		var c = f.update(Map.of("farmer", new Chunk(40, 51)));
		assertEquals(List.of(), c.remove());
		assertEquals(List.of(new Chunk(40, 51)), c.add());
		assertTrue(f.update(Map.of("farmer", new Chunk(40, 51))).isEmpty(), "same chunk: nothing to do");
		c = f.update(Map.of("farmer", new Chunk(47, 51)));
		assertEquals(List.of(new Chunk(40, 51)), c.remove());
		assertEquals(List.of(new Chunk(47, 51)), c.add());
		assertEquals(Set.of(new Chunk(47, 51)), f.held());
		c = f.update(Map.of());
		assertEquals(List.of(new Chunk(47, 51)), c.remove(), "a worker that is gone (fired, dead, unloaded) loses its ticket");
		assertEquals(Set.of(), f.held());
	}

	@Test
	void workersInOneChunkShareATicketUntilTheLastLeaves() {
		Follower<String> f = new Follower<>();
		Chunk a = new Chunk(1, 1), b = new Chunk(2, 1);
		var c = f.update(Map.of("farmer", a, "lumberjack", a));
		assertEquals(List.of(a), c.add(), "one ticket for two workers");
		c = f.update(Map.of("farmer", a, "lumberjack", b));
		assertEquals(List.of(), c.remove(), "the farmer still stands in a");
		assertEquals(List.of(b), c.add());
		c = f.update(Map.of("farmer", b, "lumberjack", b));
		assertEquals(List.of(a), c.remove());
		assertEquals(List.of(), c.add());
		assertEquals(Set.of(b), f.held());
	}

	@Test
	void twoWorkersSwappingChunksChangeNoTicket() {
		Follower<String> f = new Follower<>();
		Chunk a = new Chunk(1, 1), b = new Chunk(2, 1);
		f.update(Map.of("farmer", a, "lumberjack", b));
		assertTrue(f.update(Map.of("farmer", b, "lumberjack", a)).isEmpty());
		assertEquals(Set.of(a, b), f.held());
	}

	@Test
	void ticketsAlwaysMatchTheWorkersChunks() {
		// random walk of 5 workers: the applied changes always leave exactly one ticket per occupied chunk
		Random rnd = new Random(7);
		Follower<Integer> f = new Follower<>();
		Set<Chunk> tickets = new HashSet<>();
		Map<Integer, Chunk> pos = new HashMap<>();
		for (int step = 0; step < 2000; step++) {
			int w = rnd.nextInt(5);
			if (rnd.nextInt(20) == 0) pos.remove(w);
			else pos.put(w, new Chunk(rnd.nextInt(4), rnd.nextInt(4)));
			var c = f.update(Map.copyOf(pos));
			for (Chunk r : c.remove()) assertTrue(tickets.remove(r), "removed a ticket that was not there: " + r);
			for (Chunk a : c.add()) assertTrue(tickets.add(a), "added a ticket twice: " + a);
			assertEquals(Set.copyOf(pos.values()), tickets);
			assertEquals(tickets, f.held());
		}
		f.clear();
		assertEquals(Set.of(), f.held());
	}

	@Test
	void workCountersKeepADailyMaximum() {
		WorkCounters w = new WorkCounters();
		w.max("workers", "max_heart_distance", 40);
		w.max("workers", "max_heart_distance", 111);
		w.max("workers", "max_heart_distance", 60);
		assertEquals(Map.of("workers.max_heart_distance", 111L), w.snapshotAndReset());
		assertEquals(0, w.get("workers", "max_heart_distance"));
	}
}
