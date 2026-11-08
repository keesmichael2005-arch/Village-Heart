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
		assertEquals(2.0, m);
		assertEquals(40, ticksFor(1, m)); // anchored on stone: 40 ticks per log as before the vanilla-speed table
		assertEquals(40 * 7, ticksFor(7, m));
	}

	@Test
	void woodIsEightyAndIronHundredLogsTakeAbout2667() {
		assertEquals(80, ticksFor(1, WorkSpeed.multiplier(Optional.of(ToolTier.WOOD), AXE, 1, Needs.DEFAULT)));
		int t = ticksFor(100, WorkSpeed.multiplier(Optional.of(ToolTier.IRON), AXE, 1, Needs.DEFAULT));
		assertTrue(Math.abs(t - 2667) <= 1, "iron 100 logs: " + t);
	}

	@Test
	void levelsMultiply() {
		assertEquals(2.5, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 2, Needs.DEFAULT), 1e-12);
		assertEquals(2.0 * 1.5, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 3, Needs.DEFAULT), 1e-12);
		// a level beyond the table uses the best entry
		assertEquals(2.0 * 1.5, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 9, Needs.DEFAULT), 1e-12);
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
		assertEquals(2.0, WorkSpeed.multiplier(Optional.of(ToolTier.STONE), AXE, 1, Needs.DEFAULT));
	}

	/** T-L1-1: speed = tool x level x needs, every cell of the table from literals (DESIGN section 4). */
	@Test
	void fullToolLevelNeedsTable() {
		double[] tool = {1.0, 2.0, 2.5, 6.0, 3.0, 4.0, 4.5}; // wood stone copper gold iron diamond netherite (vanilla speed / 2)
		double[] level = {1.0, 1.25, 1.5};
		Needs[] needs = {Needs.DEFAULT, new Needs(false, 1, true, true), new Needs(true, 0, false, true), new Needs(false, 2, false, false)};
		ToolTier[] tiers = ToolTier.values();
		assertEquals(tool.length, tiers.length);
		for (int t = 0; t < tiers.length; t++) {
			for (int l = 1; l <= 3; l++) {
				for (Needs n : needs) {
					double expect = tool[t] * level[l - 1] * n.speed();
					assertEquals(expect, WorkSpeed.multiplier(Optional.of(tiers[t]), AXE, l, n), 1e-12, tiers[t] + " L" + l + " " + n);
				}
			}
		}
		for (int l = 0; l <= 4; l++) {
			assertEquals(0.0, WorkSpeed.multiplier(Optional.empty(), AXE, l, Needs.DEFAULT), "required tool missing, level " + l);
			int clamped = Math.max(1, Math.min(3, l));
			assertEquals(0.5 * level[clamped - 1], WorkSpeed.multiplier(Optional.empty(), HOE, l, Needs.DEFAULT), 1e-12);
		}
	}

	@Test
	void ironLevelThreeFellsHundredLogsIn1778Ticks() {
		double m = WorkSpeed.multiplier(Optional.of(ToolTier.IRON), AXE, 3, Needs.DEFAULT);
		assertEquals(4.5, m, 1e-12);
		int t = ticksFor(100, m);
		assertTrue(Math.abs(t - 1778) <= 1, "iron level 3, 100 logs: " + t);
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
