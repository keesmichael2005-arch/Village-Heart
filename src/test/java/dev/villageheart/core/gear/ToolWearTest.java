package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.job.ToolTier;
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

	@Test
	void smithLinesUseTheTiersOwnPace() {
		WearProfile axe = WearProfile.of(JobType.LUMBERJACK);
		WearProfile pick = WearProfile.of(JobType.MINER);
		WearProfile sword = WearProfile.of(JobType.HUNTER);
		assertTrue(line(axe, ToolTier.STONE, 44, 131));
		assertFalse(line(axe, ToolTier.STONE, 45, 131));
		assertTrue(line(axe, ToolTier.WOOD, 14, 59));
		assertFalse(line(axe, ToolTier.WOOD, 15, 59));
		assertTrue(line(pick, ToolTier.WOOD, 19, 59));
		assertFalse(line(pick, ToolTier.WOOD, 20, 59));
		assertTrue(line(pick, ToolTier.IRON, 29, 250));
		assertFalse(line(pick, ToolTier.IRON, 30, 250));
		assertTrue(line(axe, ToolTier.IRON, 111, 250));
		assertFalse(line(axe, ToolTier.IRON, 112, 250));
		assertTrue(line(sword, ToolTier.STONE, 13, 131));
		assertFalse(line(sword, ToolTier.STONE, 14, 131));
		assertTrue(ToolWear.worn(0, 59, axe.wearPerUnit(), axe.unitTicksAt(ToolTier.WOOD), lead(ToolTier.WOOD)));
		for (ToolTier tier : ToolTier.values()) assertEquals(0, dev.villageheart.core.smith.SmithRecipes.replacementLead(tier, false));
		assertEquals(0, dev.villageheart.worker.gear.GearLead.forCamp(null, null, null, null));
	}

	private static int lead(ToolTier tier) { return dev.villageheart.core.smith.SmithRecipes.replacementLead(tier, true); }

	private static boolean line(WearProfile w, ToolTier tier, int remaining, int max) {
		return ToolWear.worn(remaining, max, w.wearPerUnit(), w.unitTicksAt(tier), lead(tier));
	}
}
