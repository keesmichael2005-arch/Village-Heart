package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.JobType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolDemandTest {
	private static final HeartLocation CAMP = new HeartLocation("minecraft:overworld", 10, 64, 10);
	private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

	private static CampTool stone(int order, int remaining) { return new CampTool(order, ToolTier.STONE, ToolTier.STONE.ordinal(), remaining, 131); }
	private static CampTool iron(int order, int remaining) { return new CampTool(order, ToolTier.IRON, ToolTier.IRON.ordinal(), remaining, 250); }

	private static CampDemand axes(List<UUID> hired, Set<UUID> crafting, CampTool... tools) {
		return ToolDemand.of(CAMP, JobType.LUMBERJACK, hired, crafting, List.of(tools), 0);
	}

	@Test
	void oneHiredNoTool() {
		CampDemand d = axes(List.of(A), Set.of());
		assertEquals(1, d.needed());
		assertEquals(SlotState.MISSING, d.slots().get(0).state());
		assertEquals(Urgency.U1_MISSING_REQUIRED, d.slots().get(0).urgency());
		assertEquals(0, d.slots().get(0).key().slot());
		assertEquals(WorkerToolState.MISSING, d.stateOf(A));
	}

	@Test
	void freshToolCoversTheWorker() {
		CampDemand d = axes(List.of(A), Set.of(), stone(0, 131));
		assertEquals(0, d.needed());
		assertTrue(d.slots().isEmpty());
		assertEquals(WorkerToolState.FRESH, d.stateOf(A));
	}

	@Test
	void wornToolMakesAWornSlot() {
		CampDemand d = axes(List.of(A), Set.of(), stone(0, 10));
		assertEquals(1, d.needed());
		assertEquals(SlotState.WORN, d.slots().get(0).state());
		assertEquals(Urgency.U4_WORN_REQUIRED, d.slots().get(0).urgency());
		assertEquals(WorkerToolState.WORN, d.stateOf(A));
	}

	@Test
	void twoHiredOneFresh() {
		CampDemand d = axes(List.of(A, B), Set.of(), stone(0, 131));
		assertEquals(1, d.needed());
		assertEquals(SlotState.MISSING, d.slots().get(0).state());
		assertEquals(WorkerToolState.FRESH, d.stateOf(A));
		assertEquals(WorkerToolState.MISSING, d.stateOf(B));
	}

	@Test
	void wornIronAndFreshStone() {
		CampDemand d = axes(List.of(A, B), Set.of(), iron(0, 10), stone(1, 131));
		assertEquals(WorkerToolState.WORN, d.stateOf(A));
		assertEquals(WorkerToolState.FRESH, d.stateOf(B));
		assertEquals(1, d.needed());
		assertEquals(SlotState.WORN, d.slots().get(0).state());
		// one worker: the fresh stone axe covers the camp although the worker holds the worn iron one first
		CampDemand one = axes(List.of(A), Set.of(), iron(0, 10), stone(1, 131));
		assertEquals(0, one.needed());
		assertEquals(WorkerToolState.WORN, one.stateOf(A));
		assertTrue(ToolDemand.slotFor(A, one, Set.of()).isEmpty());
	}

	@Test
	void nobodyHiredAndUnknown() {
		assertEquals(0, axes(List.of(), Set.of(), stone(0, 131)).needed());
		CampDemand u = CampDemand.unknown(List.of(A, B));
		assertTrue(u.isUnknown());
		assertEquals(WorkerToolState.UNKNOWN, u.stateOf(A));
		assertTrue(u.slots().isEmpty());
		assertTrue(ToolDemand.slotFor(A, u, Set.of()).isEmpty());
	}

	@Test
	void aPlayersGoldenAxeIsFresh() {
		CampTool gold = new CampTool(0, ToolTier.GOLD, ToolTier.GOLD.ordinal(), 32, 32);
		assertEquals(WorkerToolState.FRESH, axes(List.of(A), Set.of(), gold).stateOf(A));
	}

	@Test
	void slotFor() {
		CampDemand d = axes(List.of(A, B), Set.of(), stone(0, 131));
		assertTrue(ToolDemand.slotFor(A, d, Set.of()).isEmpty());
		assertEquals(0, ToolDemand.slotFor(B, d, Set.of()).orElseThrow().slot());
		assertTrue(ToolDemand.slotFor(B, d, Set.of(new OrderKey(CAMP, GearNeed.AXE, 0))).isEmpty());
		CampDemand two = axes(List.of(A, B), Set.of());
		assertEquals(1, ToolDemand.slotFor(B, two, Set.of(new OrderKey(CAMP, GearNeed.AXE, 0))).orElseThrow().slot());
	}

	@Test
	void craftingWorkersAreLeftOutOfTheAllocation() {
		CampDemand d = axes(List.of(A, B), Set.of(A, B), stone(0, 131));
		assertEquals(1, d.needed());
		assertEquals(WorkerToolState.MISSING, d.stateOf(A));
		assertEquals(WorkerToolState.MISSING, d.stateOf(B));
		// a crafting first-hired worker no longer takes the fresh tool away from the second
		CampDemand one = axes(List.of(A, B), Set.of(A), stone(0, 131));
		assertEquals(WorkerToolState.FRESH, one.stateOf(B));
	}

	@Test
	void presencePerCampNotPerSlotIndex() {
		CampDemand two = axes(List.of(A, B), Set.of());
		OrderKey k0 = new OrderKey(CAMP, GearNeed.AXE, 0), k1 = new OrderKey(CAMP, GearNeed.AXE, 1);
		// slot 0 finished: its axe is fresh, needed drops to 1, slot 1's order is still held
		CampDemand afterFinish = axes(List.of(A, B), Set.of(B), stone(0, 131));
		assertEquals(1, afterFinish.needed());
		Map<OrderKey, SlotPresence> p = ToolDemand.presence(afterFinish, List.of(new OpenOrder(k1, 900, 5)));
		assertEquals(SlotPresence.HELD, p.get(k1));
		// two open orders and needed 1: the less advanced one is covered
		Map<OrderKey, SlotPresence> q = ToolDemand.presence(afterFinish, List.of(new OpenOrder(k0, 2000, 1), new OpenOrder(k1, 900, 5)));
		assertEquals(SlotPresence.HELD, q.get(k0));
		assertEquals(SlotPresence.COVERED, q.get(k1));
		// the whole camp unreadable
		Map<OrderKey, SlotPresence> u = ToolDemand.presence(CampDemand.unknown(List.of(A, B)), List.of(new OpenOrder(k0, 2000, 1)));
		assertEquals(SlotPresence.UNKNOWN, u.get(k0));
		assertEquals(2, two.needed());
	}

	@Test
	void hunterAndFarmerUrgency() {
		List<CampTool> none = new ArrayList<>();
		CampDemand f = ToolDemand.of(CAMP, JobType.FARMER, List.of(A), Set.of(), none, 0);
		assertEquals(Urgency.U5_HOE, f.slots().get(0).urgency());
		assertEquals(GearNeed.HOE, f.slots().get(0).key().need());
		CampDemand h = ToolDemand.of(CAMP, JobType.HUNTER, List.of(A), Set.of(), none, 0);
		assertEquals(GearNeed.SWORD, h.slots().get(0).key().need());
	}
}
