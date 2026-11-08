package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import org.junit.jupiter.api.Test;

class FieldIndexCostTest {
	private static final Pos JB = new Pos(0, 64, 0);

	private static int columnsInDisc() {
		int n = 0, r = Balance.FARM_RADIUS;
		for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (x * x + z * z <= r * r) n++;
		return n;
	}

	@Test
	void aFullSweepStaysUnderTheColumnBound() {
		int columns = columnsInDisc();
		assertEquals(1793, columns, "the disc of radius 24");
		assertEquals(columns, FieldIndex.columnsFor(Balance.FARM_RADIUS).length);
		FakeFieldGrid g = new FakeFieldGrid();
		FieldIndex idx = new FieldIndex(JB);
		long limit = (long) columns * (2 * Balance.FARM_DY + 1) * FieldIndex.READS_PER_CELL;
		while (!idx.sweepComplete()) idx.sweep(g, Balance.FIELD_SWEEP_READS_PER_TICK, 0);
		assertTrue(g.reads <= limit, g.reads + " > " + limit);
		assertTrue(g.reads > limit / 2, "and it really reads the area: " + g.reads);
	}

	@Test
	void eachCallStaysInItsBudget() {
		FakeFieldGrid g = new FakeFieldGrid();
		FieldIndex idx = new FieldIndex(JB);
		int guard = 0;
		while (!idx.sweepComplete() && guard++ < 100_000) {
			long before = g.reads;
			int spent = idx.sweep(g, Balance.FIELD_SWEEP_READS_PER_TICK, 0);
			assertEquals(g.reads - before, spent);
			assertTrue(spent < Balance.FIELD_SWEEP_READS_PER_TICK + FieldIndex.READS_PER_CELL);
		}
		FakeFieldGrid g2 = new FakeFieldGrid();
		for (int x = -5; x <= 5; x++) g2.set(x, 64, 3, FieldCell.crop(CropKind.WHEAT, 1));
		FieldIndex idx2 = new FieldIndex(JB);
		while (!idx2.sweepComplete()) idx2.sweep(g2, Integer.MAX_VALUE, 0);
		for (int i = 0; i < 20; i++) {
			long before = g2.reads;
			int spent = idx2.refresh(g2, Balance.FIELD_REFRESH_READS_PER_TICK);
			assertEquals(g2.reads - before, spent);
			assertTrue(spent <= Balance.FIELD_REFRESH_READS_PER_TICK);
		}
	}

	@Test
	void indexesSharingOneBudgetNeverExceedIt() {
		// the server-wide budget: a round-robin that hands each index what is left, as FarmRuntime does
		FakeFieldGrid g = new FakeFieldGrid();
		FieldIndex[] all = {new FieldIndex(JB), new FieldIndex(new Pos(30, 64, 0)), new FieldIndex(new Pos(0, 64, 30))};
		int rr = 0;
		for (int tick = 0; tick < 200; tick++) {
			long before = g.reads;
			int left = Balance.FIELD_SWEEP_READS_PER_TICK;
			for (int k = 0; k < all.length && left > 0; k++) {
				FieldIndex idx = all[(rr + k) % all.length];
				if (!idx.sweepComplete()) left -= idx.sweep(g, left, tick);
			}
			rr++;
			assertTrue(g.reads - before <= Balance.FIELD_SWEEP_READS_PER_TICK + FieldIndex.READS_PER_CELL, "tick " + tick);
		}
	}
}
