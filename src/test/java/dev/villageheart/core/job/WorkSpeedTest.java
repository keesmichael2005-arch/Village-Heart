package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.worker.Needs;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkSpeedTest {
	private static final ToolRequirement AXE = new ToolRequirement(ToolKind.AXE, true);
	private static final ToolRequirement HOE = new ToolRequirement(ToolKind.HOE, false);
	private static final ToolRequirement FARM_HOE = new ToolRequirement(ToolKind.HOE, ToolRequirement.Mode.BONUS);

	private static int ticksFor(int units, double mult) {
		WorkClock c = new WorkClock(Balance.BASE_TICKS_PER_LOG);
		int done = 0, t = 0;
		while (done < units && t < 1_000_000) {
			t++;
			done += c.tick(mult);
		}
		return t;
	}

	@Test
	void stoneAxeFellsALogInFortyTicks() {
		double m = WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 1, Needs.DEFAULT);
		assertEquals(1.25, m);
		assertEquals(40, ticksFor(1, m));
		assertEquals(40 * 7, ticksFor(7, m));
	}

	@Test
	void woodIsFiftyAndIronHundredLogsTakeAbout3334() {
		assertEquals(50, ticksFor(1, WorkSpeed.multiplier(Optional.of(ToolTier.WOOD), AXE, 1, Needs.DEFAULT)));
		int t = ticksFor(100, WorkSpeed.multiplier(Optional.of(ToolTier.IRON), AXE, 1, Needs.DEFAULT));
		assertTrue(Math.abs(t - 3334) <= 1, "iron 100 logs: " + t);
	}

	@Test
	void levelsMultiply() {
		assertEquals(1.5625, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 2, Needs.DEFAULT), 1e-12);
		assertEquals(1.25 * 1.5, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 3, Needs.DEFAULT), 1e-12);
		// a level beyond the table uses the best entry
		assertEquals(1.25 * 1.5, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 9, Needs.DEFAULT), 1e-12);
	}

	@Test
	void aMissingRequiredToolNeverCompletesAUnit() {
		double m = WorkSpeed.multiplier(Optional.empty(), AXE, 1, Needs.DEFAULT);
		assertEquals(0.0, m);
		WorkClock c = new WorkClock(Balance.BASE_TICKS_PER_LOG);
		for (int i = 0; i < 10_000; i++) assertEquals(0, c.tick(m));
	}

	@Test
	void aMissingOptionalToolIsHalfSpeed() {
		assertEquals(Balance.NO_TOOL_SPEED, WorkSpeed.multiplier(Optional.empty(), HOE, 1, Needs.DEFAULT));
		assertEquals(0.5, WorkSpeed.multiplier(Optional.empty(), HOE, 1, Needs.DEFAULT));
	}

	@Test
	void aBonusToolIsFlatAndNeverHalvesTheJob() {
		// no hoe: full speed, 20 ticks per harvest
		double none = WorkSpeed.multiplier(Optional.empty(), FARM_HOE, 1, Needs.DEFAULT);
		assertEquals(1.0, none);
		assertEquals(20, ticksFor(1, none, Balance.FARM_TICKS_HARVEST));
		// a wooden hoe: 1.25, 16 ticks
		double wood = WorkSpeed.multiplier(Optional.of(ToolTier.WOOD), FARM_HOE, 1, Needs.DEFAULT);
		assertEquals(1.25, wood);
		assertEquals(16, ticksFor(1, wood, Balance.FARM_TICKS_HARVEST));
		// the tier does not matter
		for (ToolTier t : ToolTier.values()) assertEquals(1.25, WorkSpeed.multiplier(Optional.of(t), FARM_HOE, 1, Needs.DEFAULT), t.name());
		// the other rows are unchanged
		assertEquals(0.0, WorkSpeed.multiplier(Optional.empty(), AXE, 1, Needs.DEFAULT));
		assertEquals(0.5, WorkSpeed.multiplier(Optional.empty(), HOE, 1, Needs.DEFAULT));
		assertEquals(1.25, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 1, Needs.DEFAULT));
	}

	private static int ticksFor(int units, double mult, double unitCost) {
		WorkClock c = new WorkClock(unitCost);
		int done = 0, t = 0;
		while (done < units && t < 1_000_000) {
			t++;
			done += c.tick(mult);
		}
		return t;
	}
}
