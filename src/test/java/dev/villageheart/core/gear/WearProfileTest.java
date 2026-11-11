package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.JobType;
import org.junit.jupiter.api.Test;

/** Ticks per unit once tool speed is half of vanilla (M19). */
class WearProfileTest {
	@Test
	void tierPacedJobsScaleOffStone() {
		WearProfile axe = WearProfile.of(JobType.LUMBERJACK);
		assertEquals(80, axe.unitTicksAt(ToolTier.WOOD));
		assertEquals(40, axe.unitTicksAt(ToolTier.STONE));
		assertEquals(32, axe.unitTicksAt(ToolTier.COPPER));
		assertEquals(27, axe.unitTicksAt(ToolTier.IRON));
		assertEquals(20, axe.unitTicksAt(ToolTier.DIAMOND));
		WearProfile pick = WearProfile.of(JobType.MINER);
		assertEquals(4000, pick.unitTicksAt(ToolTier.WOOD));
		assertEquals(2000, pick.unitTicksAt(ToolTier.STONE));
		assertEquals(1334, pick.unitTicksAt(ToolTier.IRON));
		assertEquals(1000, pick.unitTicksAt(ToolTier.DIAMOND));
	}

	@Test
	void hunterAndFarmerIgnoreTheTier() {
		for (ToolTier tier : ToolTier.values()) {
			assertEquals(3000, WearProfile.of(JobType.HUNTER).unitTicksAt(tier));
			assertEquals(20, WearProfile.of(JobType.FARMER).unitTicksAt(tier));
		}
	}
}
