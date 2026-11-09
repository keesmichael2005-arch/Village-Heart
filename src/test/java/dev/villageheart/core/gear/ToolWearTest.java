package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.worker.JobType;
import org.junit.jupiter.api.Test;

class ToolWearTest {
	private static boolean worn(JobType job, int remaining, int max) {
		WearProfile w = WearProfile.of(job);
		return ToolWear.worn(remaining, max, w.wearPerUnit(), w.unitTicks(), 0);
	}

	@Test
	void selfCraftLinesAtLeadZero() {
		assertTrue(worn(JobType.LUMBERJACK, 13, 131));
		assertFalse(worn(JobType.LUMBERJACK, 14, 131));
		assertTrue(worn(JobType.LUMBERJACK, 5, 59));
		assertFalse(worn(JobType.LUMBERJACK, 6, 59));
		assertTrue(worn(JobType.MINER, 19, 59));
		assertFalse(worn(JobType.MINER, 20, 59));
		assertTrue(worn(JobType.MINER, 19, 131));
		assertFalse(worn(JobType.MINER, 20, 131));
		assertTrue(worn(JobType.MINER, 24, 250));
		assertTrue(worn(JobType.MINER, 24, 250));
		assertFalse(worn(JobType.MINER, 25, 250));
		assertTrue(worn(JobType.HUNTER, 9, 59));
		assertFalse(worn(JobType.HUNTER, 10, 59));
		assertTrue(worn(JobType.HUNTER, 38, 384));
		assertFalse(worn(JobType.HUNTER, 39, 384));
		assertTrue(worn(JobType.FARMER, 5, 59));
		assertFalse(worn(JobType.FARMER, 6, 59));
	}

	@Test
	void zeroLeftIsWornAndUndamageableNever() {
		assertTrue(worn(JobType.LUMBERJACK, 0, 59));
		assertFalse(worn(JobType.LUMBERJACK, 0, 0));
		assertFalse(ToolWear.worn(0, 0, 1, 40, 1800));
	}

	@Test
	void theSmithLeadShape() {
		WearProfile w = WearProfile.of(JobType.LUMBERJACK);
		assertTrue(ToolWear.worn(44, 131, w.wearPerUnit(), w.unitTicks(), 1800));
		assertFalse(ToolWear.worn(45, 131, w.wearPerUnit(), w.unitTicks(), 1800));
	}
}
