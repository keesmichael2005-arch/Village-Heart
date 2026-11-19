package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.worker.JobType;
import org.junit.jupiter.api.Test;

/** Slot 0 is the tool on the four tool jobs. Goods never fill it. A dump starts when the cargo slots are full. */
class PocketRulesTest {
	@Test
	void toolJobsReserveSlotZeroAndTheOthersUseAllEight() {
		for (JobType job : new JobType[] {JobType.LUMBERJACK, JobType.MINER, JobType.HUNTER, JobType.FARMER}) {
			assertTrue(PocketRules.reservesToolSlot(job), job.name());
			assertEquals(7, PocketRules.cargoSlots(job));
			assertFalse(PocketRules.acceptsGoods(job, 0));
			assertTrue(PocketRules.acceptsGoods(job, 1));
		}
		for (JobType job : new JobType[] {JobType.BUILDER, JobType.COOK, JobType.MERCHANT, JobType.BLACKSMITH, JobType.GUARD}) {
			assertFalse(PocketRules.reservesToolSlot(job), job.name());
			assertEquals(8, PocketRules.cargoSlots(job));
			assertTrue(PocketRules.acceptsGoods(job, 0));
		}
	}

	@Test
	void cargoIsFullWhenEveryGoodsSlotIsTaken() {
		boolean[] seven = occupied(7);
		assertTrue(PocketRules.cargoFull(JobType.LUMBERJACK, seven), "an empty tool slot is not room for logs");
		assertFalse(PocketRules.cargoFull(JobType.LUMBERJACK, occupied(6)));
		assertFalse(PocketRules.cargoFull(JobType.BUILDER, seven));
		assertTrue(PocketRules.cargoFull(JobType.BUILDER, occupied(8)));
		assertFalse(PocketRules.cargoFull(JobType.GUARD, occupied(0)));
	}

	@Test
	void goodsLandInTheFirstFreeCargoSlot() {
		assertEquals(1, PocketRules.placeGoods(JobType.LUMBERJACK, new boolean[] {true, false, false, false, false, false, false, false}));
		assertEquals(2, PocketRules.placeGoods(JobType.FARMER, new boolean[] {false, true, false, false, false, false, false, false}));
		assertEquals(-1, PocketRules.placeGoods(JobType.MINER, new boolean[] {false, true, true, true, true, true, true, true}));
		assertEquals(0, PocketRules.placeGoods(JobType.COOK, new boolean[] {false, true, false, false, false, false, false, false}));
	}

	/** {@code n} leading slots occupied. A tool job's slot 0 is not a cargo slot, so seven means slots 1..7. */
	private static boolean[] occupied(int n) {
		boolean[] slots = new boolean[PocketRules.SLOTS];
		if (n >= 8) {
			for (int i = 0; i < 8; i++) slots[i] = true;
			return slots;
		}
		for (int i = 1; i <= n && i < 8; i++) slots[i] = true;
		if (n == 8) for (int i = 0; i < 8; i++) slots[i] = true;
		return slots;
	}
}
