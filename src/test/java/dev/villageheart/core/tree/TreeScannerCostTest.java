package dev.villageheart.core.tree;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Deterministic read counts (no wall clock): the search must fit the tick budget. */
class TreeScannerCostTest {
	@Test
	void aMaxTreeCostsAtMost32ReadsPerLog() {
		FakeTreeGrid g = new FakeTreeGrid();
		// 4 x 1 trunk 48 high = 192 logs, with leaves on one side so the flood does not stop early
		int n = 0;
		for (int y = 1; y <= 48 && n < 192; y++) for (int dx = 0; dx < 4; dx++) {
			g.set(dx, y, 0, TreeCell.log(Species.OAK));
			n++;
		}
		for (int y = 1; y <= 48; y++) g.set(0, y, 1, TreeCell.naturalLeaves(Species.OAK));
		g.reads = 0;
		TreeScanner.scan(g, new Pos(0, 1, 0));
		assertTrue(g.reads <= 192 * (26 + 6), "reads " + g.reads);
		assertTrue(g.reads > 192 * 20, "the test must really read: " + g.reads);
	}

	@Test
	void aFullRadius32SweepOfADenseForestStaysUnder60000Reads() {
		FakeTreeGrid g = new FakeTreeGrid();
		Random r = new Random(7);
		// a tree every 5 blocks (5x5 canopies that touch): about 170 trees in the circle, denser than any vanilla forest
		for (int x = -32; x <= 32; x += 5) for (int z = -32; z <= 32; z += 5) g.oak(x + r.nextInt(2), z + r.nextInt(2), 5 + r.nextInt(3));
		TreeFinder f = new TreeFinder(new Pos(0, 1, 0), 32, 16);
		g.reads = 0;
		int found = 0;
		while (true) {
			TreeFinder.Result res = f.step(g, 2048, p -> false);
			if (res instanceof TreeFinder.Found) found++;
			else if (res == TreeFinder.Progress.EXHAUSTED) break;
		}
		assertTrue(found > 100, "trees found " + found);
		System.out.println("[m9] dense forest sweep: " + found + " trees, " + g.reads + " reads");
		assertTrue(g.reads <= 60_000, "reads " + g.reads);
	}
}
