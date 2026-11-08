package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FieldIndexTest {
	private static final Pos JB = new Pos(0, 64, 0);
	private static final FieldCell WHEAT7 = FieldCell.crop(CropKind.WHEAT, 7);

	private static FakeFieldGrid world() {
		FakeFieldGrid g = new FakeFieldGrid();
		g.field(10, 64, 0, 4, WHEAT7);                       // 9x9, water (OTHER) in the centre: 80 cells
		g.set(10, 64, 8, FieldCell.EMPTY);                   // empty farmland
		g.set(11, 64, 8, FieldCell.crop(CropKind.BEETROOTS, 1));
		g.set(12, 64, 8, FieldCell.crop(CropKind.CARROTS, 3));
		g.set(13, 64, 8, FieldCell.crop(CropKind.POTATOES, 0));
		g.set(14, 64, 8, FieldCell.OTHER);                   // a crop on dirt reads OTHER
		g.set(24, 64, 0, WHEAT7);                            // exactly at the edge: in
		g.set(25, 64, 0, WHEAT7);                            // out
		g.set(17, 64, 17, WHEAT7);                           // out (24.04)
		g.set(0, 73, 5, WHEAT7);                             // dy +9: out
		g.set(0, 72, 5, WHEAT7);                             // dy +8: in
		return g;
	}

	private static int sweepAll(FieldIndex idx, FakeFieldGrid g, int budget) {
		int calls = 0;
		while (!idx.sweepComplete()) {
			idx.sweep(g, budget, 0);
			calls++;
		}
		return calls;
	}

	@Test
	void theIndexHoldsExactlyTheCellsOfTheArea() {
		FakeFieldGrid g = world();
		FieldIndex idx = new FieldIndex(JB);
		sweepAll(idx, g, Integer.MAX_VALUE);
		List<FieldIndex.Indexed> cells = idx.cells();
		// 80 wheat + empty + beetroot + carrot + potato + the cell at 24 + the cell at dy +8
		assertEquals(86, cells.size());
		for (FieldIndex.Indexed i : cells) assertTrue(FieldArea.contains(JB, i.pos()), i.toString());
		List<Pos> poses = cells.stream().map(FieldIndex.Indexed::pos).collect(Collectors.toList());
		assertFalse(poses.contains(new Pos(25, 64, 0)));
		assertFalse(poses.contains(new Pos(17, 64, 17)));
		assertFalse(poses.contains(new Pos(0, 73, 5)));
		assertTrue(poses.contains(new Pos(24, 64, 0)));
		assertTrue(poses.contains(new Pos(0, 72, 5)));
		assertFalse(poses.contains(new Pos(14, 64, 8)), "a crop that is not on farmland (OTHER) is not indexed");
		assertFalse(poses.contains(new Pos(10, 64, 0)), "water");
		List<Pos> sorted = new ArrayList<>(poses);
		java.util.Collections.sort(sorted);
		assertEquals(sorted, poses, "Pos order");
		assertTrue(cells.contains(new FieldIndex.Indexed(new Pos(10, 64, 8), FieldCell.EMPTY)));
	}

	@Test
	void anUnloadedColumnIsSkippedAndFoundAfterItLoads() {
		FakeFieldGrid g = new FakeFieldGrid();
		g.set(5, 64, 5, WHEAT7);
		g.set(6, 64, 5, WHEAT7);
		g.unload(5, 5);
		FieldIndex idx = new FieldIndex(JB);
		sweepAll(idx, g, Integer.MAX_VALUE);
		assertEquals(1, idx.size());
		assertTrue(idx.sawUnloaded());
		g.load(5, 5);
		idx.restartSweep();
		sweepAll(idx, g, Integer.MAX_VALUE);
		assertEquals(2, idx.size());
		assertFalse(idx.sawUnloaded());
	}

	@Test
	void refreshPicksUpChanges() {
		FakeFieldGrid g = world();
		FieldIndex idx = new FieldIndex(JB);
		sweepAll(idx, g, Integer.MAX_VALUE);
		g.set(8, 64, 0, FieldCell.crop(CropKind.WHEAT, 0));
		g.set(9, 64, 0, FieldCell.OTHER);
		for (int i = 0; i < 200; i++) idx.refresh(g, 64);
		FieldCell now = idx.cells().stream().filter(c -> c.pos().equals(new Pos(8, 64, 0))).findFirst().orElseThrow().cell();
		assertEquals(0, now.age());
		assertTrue(idx.cells().stream().noneMatch(c -> c.pos().equals(new Pos(9, 64, 0))), "a cell that is no longer a crop leaves the index");
	}

	@Test
	void aSmallBudgetGivesTheSameResult() {
		FakeFieldGrid g1 = world(), g2 = world();
		FieldIndex a = new FieldIndex(JB), b = new FieldIndex(JB);
		sweepAll(a, g1, Integer.MAX_VALUE);
		int calls = sweepAll(b, g2, 50);
		assertTrue(calls > 100, "the small budget needs many calls: " + calls);
		assertEquals(a.cells(), b.cells());
	}

	@Test
	void theCellCapKeepsTheNearest() {
		FakeFieldGrid g = new FakeFieldGrid();
		int n = 0;
		for (int x = -24; x <= 24; x++) for (int z = -10; z <= 10; z++) for (int y = 60; y <= 68; y++) {
			if (x * x + z * z > 576) continue;
			g.set(x, y, z, WHEAT7);
			n++;
		}
		FieldIndex idx = new FieldIndex(JB);
		sweepAll(idx, g, Integer.MAX_VALUE);
		assertTrue(n > Balance.MAX_FIELD_CELLS);
		assertEquals(Balance.MAX_FIELD_CELLS, idx.size());
		assertTrue(idx.capped());
		assertTrue(idx.cells().stream().anyMatch(c -> c.pos().equals(new Pos(0, 64, 5))), "near cells are in");
		assertTrue(idx.cells().stream().noneMatch(c -> c.pos().x() == 24), "the farthest columns are cut");
	}

	@Test
	void noFarmlandAfterACompleteSweep() {
		FieldIndex idx = new FieldIndex(JB);
		assertFalse(idx.sweepComplete());
		sweepAll(idx, new FakeFieldGrid(), Integer.MAX_VALUE);
		assertTrue(idx.sweepComplete());
		assertEquals(0, idx.size());
		assertFalse(idx.sawUnloaded());
	}
}
