package dev.villageheart.core.tree;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.TreeScan.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TreeFinderTest {
	private static final Pos C = new Pos(0, 1, 0);

	private static List<Pos> findAll(FakeTreeGrid g, int budget, Predicate<Pos> skip) {
		TreeFinder f = new TreeFinder(C, 32, 16);
		List<Pos> bases = new ArrayList<>();
		for (int guard = 0; guard < 1_000_000; guard++) {
			TreeFinder.Result r = f.step(g, budget, skip);
			if (r instanceof TreeFinder.Found found) bases.add(found.tree().base().get(0));
			else if (r == TreeFinder.Progress.EXHAUSTED) return bases;
		}
		throw new AssertionError("never exhausted");
	}

	@Test
	void treesAreFoundNearestFirst() {
		FakeTreeGrid g = new FakeTreeGrid().oak(31, 0, 5).oak(5, 0, 5).oak(0, 12, 5);
		assertEquals(List.of(new Pos(5, 1, 0), new Pos(0, 1, 12), new Pos(31, 1, 0)), findAll(g, 1_000_000, p -> false));
	}

	@Test
	void radiusIsEuclideanAndInclusive() {
		FakeTreeGrid g = new FakeTreeGrid().oak(33, 0, 5).oak(32, 0, 5);
		List<Pos> found = findAll(g, 1_000_000, p -> false);
		assertEquals(List.of(new Pos(32, 1, 0)), found);
		FakeTreeGrid corner = new FakeTreeGrid().oak(23, 23, 5); // 32.5 away
		assertEquals(List.of(), findAll(corner, 1_000_000, p -> false));
		FakeTreeGrid inside = new FakeTreeGrid().oak(22, 22, 5); // 31.1 away
		assertEquals(1, findAll(inside, 1_000_000, p -> false).size());
	}

	@Test
	void verticalLimitIs16() {
		FakeTreeGrid up = new FakeTreeGrid();
		// a tree on a hill 17 blocks above the centre (soil below it)
		for (int y = 1; y <= 17; y++) up.set(6, y, 0, TreeCell.SOIL);
		for (int y = 18; y <= 22; y++) up.set(6, y, 0, TreeCell.log(Species.OAK));
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) up.set(6 + dx, 21, dz, TreeCell.naturalLeaves(Species.OAK));
		assertEquals(List.of(), findAll(up, 1_000_000, p -> false));
		FakeTreeGrid ok = new FakeTreeGrid();
		for (int y = 1; y <= 16; y++) ok.set(6, y, 0, TreeCell.SOIL);
		for (int y = 17; y <= 21; y++) ok.set(6, y, 0, TreeCell.log(Species.OAK));
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) ok.set(6 + dx, 20, dz, TreeCell.naturalLeaves(Species.OAK));
		assertEquals(1, findAll(ok, 1_000_000, p -> false).size());
	}

	@Test
	void aTreeUnderAnotherTreesBranchIsFound() {
		FakeTreeGrid g = new FakeTreeGrid().oak(10, 0, 5);
		// a high horizontal branch of another tree passes over a small tree at x=14
		for (int x = 12; x <= 16; x++) g.set(x, 9, 0, TreeCell.log(Species.OAK));
		g.set(12, 8, 0, TreeCell.log(Species.OAK)); // diagonal to the trunk's canopy log chain
		for (int y = 1; y <= 8; y++) g.set(11, y, 0, TreeCell.log(Species.OAK)); // makes the branch part of the big tree
		g.oak(14, 0, 4);
		List<Pos> bases = findAll(g, 1_000_000, p -> false);
		assertTrue(bases.contains(new Pos(14, 1, 0)), "found " + bases);
	}

	@Test
	void budgetDoesNotChangeTheSequence() {
		FakeTreeGrid g = new FakeTreeGrid();
		for (int i = 0; i < 8; i++) g.oak(3 + 3 * i, (i * 7) % 11 - 5, 5);
		assertEquals(findAll(g, 1_000_000, p -> false), findAll(g, 50, p -> false));
		assertEquals(findAll(g, 1_000_000, p -> false), findAll(g, 1, p -> false));
	}

	@Test
	void skipBaseIsHonoured() {
		FakeTreeGrid g = new FakeTreeGrid().oak(5, 0, 5).oak(12, 0, 5);
		List<Pos> found = findAll(g, 1_000_000, p -> p.x() == 5);
		assertEquals(List.of(new Pos(12, 1, 0)), found);
	}

	@Test
	void exhaustedStaysExhaustedAndPausedIsReported() {
		FakeTreeGrid g = new FakeTreeGrid();
		TreeFinder f = new TreeFinder(C, 32, 16);
		assertEquals(TreeFinder.Progress.PAUSED, f.step(g, 10, p -> false));
		TreeFinder.Result last;
		do last = f.step(g, 100_000, p -> false); while (last == TreeFinder.Progress.PAUSED);
		assertEquals(TreeFinder.Progress.EXHAUSTED, last);
		assertEquals(TreeFinder.Progress.EXHAUSTED, f.step(g, 100, p -> false));
	}

	@Test
	void aPlayerWallIsScannedOnceNotOncePerColumn() {
		FakeTreeGrid g = new FakeTreeGrid().cabin(5, 5, 9, 9, 4);
		TreeFinder f = new TreeFinder(C, 32, 16);
		g.reads = 0;
		while (f.step(g, 100_000, p -> false) != TreeFinder.Progress.EXHAUSTED) { /* sweep */ }
		// 28 wall columns x 4 logs = 112 logs: one flood is 112 x 26 = 2912 reads; the sweep itself ~ 3200 columns x few reads
		assertTrue(g.reads < 40_000, "reads " + g.reads);
	}

	@Test
	void unknownIsRemembered() {
		FakeTreeGrid g = new FakeTreeGrid().oak(8, 0, 5).unload(9, 3, 0);
		TreeFinder f = new TreeFinder(C, 32, 16);
		while (f.step(g, 100_000, p -> false) != TreeFinder.Progress.EXHAUSTED) { /* sweep */ }
		assertTrue(f.sawUnknown());
	}
}
