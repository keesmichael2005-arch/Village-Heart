package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GearDemandPlannerTest {
	private static final HeartLocation CAMP = new HeartLocation("minecraft:overworld", 10, 64, 10);

	private static CancelCheck run(int start, SlotPresence... seq) {
		CancelCheck c = new CancelCheck(start, Optional.empty());
		for (SlotPresence p : seq) c = GearDemandPlanner.cancel(p, c.consecutiveDefinite());
		return c;
	}

	@Test
	void heldResets() {
		assertEquals(0, run(1, SlotPresence.HELD).consecutiveDefinite());
	}

	@Test
	void twoDefiniteInARow() {
		CancelCheck once = run(0, SlotPresence.COVERED);
		assertEquals(1, once.consecutiveDefinite());
		assertTrue(once.reason().isEmpty());
		assertEquals(CancelReason.COVERED, run(0, SlotPresence.COVERED, SlotPresence.COVERED).reason().orElseThrow());
		assertEquals(CancelReason.HIRED_DROPPED, run(0, SlotPresence.HIRED_DROPPED, SlotPresence.HIRED_DROPPED).reason().orElseThrow());
		assertEquals(CancelReason.BUILDING_UNREGISTERED,
				run(0, SlotPresence.BUILDING_UNREGISTERED, SlotPresence.BUILDING_UNREGISTERED).reason().orElseThrow());
	}

	@Test
	void unknownKeepsTheCount() {
		assertEquals(CancelReason.COVERED, run(0, SlotPresence.COVERED, SlotPresence.UNKNOWN, SlotPresence.COVERED).reason().orElseThrow());
		assertTrue(run(0, SlotPresence.COVERED, SlotPresence.HELD, SlotPresence.COVERED).reason().isEmpty());
		assertTrue(run(0, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN).reason().isEmpty());
	}

	@Test
	void surplusIsTheLeastAdvanced() {
		OrderKey k0 = new OrderKey(CAMP, GearNeed.AXE, 0), k1 = new OrderKey(CAMP, GearNeed.AXE, 1);
		assertEquals(List.of(k0), GearDemandPlanner.surplus(List.of(new OpenOrder(k0, 600, 1), new OpenOrder(k1, 2000, 2)), 1));
		// equal progress: the later start
		assertEquals(List.of(k1), GearDemandPlanner.surplus(List.of(new OpenOrder(k0, 600, 1), new OpenOrder(k1, 600, 9)), 1));
		assertTrue(GearDemandPlanner.surplus(List.of(new OpenOrder(k0, 600, 1)), 1).isEmpty());
	}
}
