package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.job.ToolRequirement.Mode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelfCraftPlannerTest {
	private static final HeartLocation CAMP = new HeartLocation("minecraft:overworld", 10, 64, 10);
	private static final OrderKey KEY = new OrderKey(CAMP, GearNeed.PICKAXE, 0);
	private static final CraftPlan PLAN = new CraftPlan(ToolRecipes.of(GearNeed.PICKAXE, CraftTier.STONE),
			List.of(new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:stick", 2)), 0, 0, List.of());
	private static final SelfCraft SAVED = new SelfCraft(new UUID(0, 1), new GearCraft("minecraft:stone_pickaxe", KEY, 100, List.of()), false);

	/** A MISSING required miner at the boundary with a plan, a free chest and a morning clock: row 12. */
	private static Builder base() { return new Builder(); }

	private static final class Builder {
		WorkerToolState own = WorkerToolState.MISSING;
		Optional<OrderKey> slot = Optional.of(KEY);
		SlotState slotState = SlotState.MISSING;
		Mode mode = Mode.REQUIRED;
		Optional<SelfCraft> saved = Optional.empty();
		Optional<CancelReason> cancel = Optional.empty();
		Optional<PendingFetch> fetching = Optional.empty();
		SlotPresence presence = SlotPresence.HELD;
		boolean boundary = true, idle = true, smith = false, stored = false, space = true;
		long open = 0, day = 2000;
		Optional<CraftPlan> plan = Optional.of(PLAN);

		Builder own(WorkerToolState s) { own = s; return this; }
		Builder mode(Mode m) { mode = m; return this; }
		Builder saved() { saved = Optional.of(SAVED); return this; }
		Builder fetching() { fetching = Optional.of(new PendingFetch(KEY, PLAN, false, 0)); return this; }
		Builder day(long d) { day = d; return this; }
		Builder noPlan() { plan = Optional.empty(); return this; }

		SelfCraftDecision go() {
			return SelfCraftPlanner.decide(new SelfCraftFacts(own, slot, slotState, mode, saved, cancel, fetching, presence, boundary, idle,
					smith, open, stored, space, plan, day));
		}
	}

	@Test
	void startsWhenEverythingIsRight() {
		assertEquals(new SelfCraftDecision.Start(PLAN), base().go());
	}

	@Test
	void fetchRows() {
		assertEquals(SelfCraftDecision.KEEP_FETCHING, base().fetching().go());
		// rows 6-11 never abort a fetch mid-walk: full chest, no plan, late day
		Builder b = base().fetching().noPlan().day(Balance.WORK_END - 200);
		b.space = false;
		assertEquals(SelfCraftDecision.KEEP_FETCHING, b.go());
		Builder covered = base().fetching();
		covered.presence = SlotPresence.COVERED;
		assertEquals(SelfCraftDecision.ABORT_FETCH, covered.go());
		assertEquals(SelfCraftDecision.ABORT_FETCH, base().fetching().day(Balance.WORK_END).go());
	}

	@Test
	void savedCraftRows() {
		Builder cancel = base().saved();
		cancel.cancel = Optional.of(CancelReason.HIRED_DROPPED);
		assertEquals(new SelfCraftDecision.Cancel(CancelReason.HIRED_DROPPED), cancel.go());
		Builder unknown = base().saved();
		unknown.slotState = SlotState.UNKNOWN;
		assertEquals(SelfCraftDecision.PAUSE, unknown.go());
		Builder notBoundary = base().saved();
		notBoundary.boundary = false;
		assertEquals(SelfCraftDecision.NONE, notBoundary.go());
		assertEquals(SelfCraftDecision.CONTINUE, base().saved().go());
		// a farmer's saved craft with farm work goes quiet
		Builder farmer = base().saved().mode(Mode.BONUS);
		farmer.idle = false;
		assertEquals(SelfCraftDecision.NONE, farmer.go());
		farmer.idle = true;
		assertEquals(SelfCraftDecision.CONTINUE, farmer.go());
	}

	@Test
	void freshWorkerNeverCrafts() {
		assertEquals(SelfCraftDecision.NONE, base().own(WorkerToolState.FRESH).go());
		assertEquals(SelfCraftDecision.NONE, base().own(WorkerToolState.UNKNOWN).go());
		Builder noSlot = base();
		noSlot.slot = Optional.empty();
		assertEquals(SelfCraftDecision.NONE, noSlot.go());
	}

	@Test
	void boundaryAndBonusIdle() {
		Builder b = base();
		b.boundary = false;
		assertEquals(SelfCraftDecision.NONE, b.go());
		Builder farmer = base().own(WorkerToolState.MISSING).mode(Mode.BONUS);
		farmer.idle = false;
		assertEquals(SelfCraftDecision.NONE, farmer.go());
		farmer.idle = true;
		assertInstanceOf(SelfCraftDecision.Start.class, farmer.go());
	}

	@Test
	void smithSeam() {
		Builder b = base();
		b.smith = true;
		assertEquals(SelfCraftDecision.NONE, b.go());
		b.open = 3000;
		assertInstanceOf(SelfCraftDecision.Start.class, b.go());
	}

	@Test
	void fullChest() {
		Builder missing = base();
		missing.space = false;
		assertEquals(new SelfCraftDecision.Blocked(BlockReason.NO_SPACE), missing.go());
		// a full chest with a usable Storage tool: still blocked, never fetched
		missing.stored = true;
		assertEquals(new SelfCraftDecision.Blocked(BlockReason.NO_SPACE), missing.go());
		Builder worn = base().own(WorkerToolState.WORN);
		worn.space = false;
		assertEquals(SelfCraftDecision.NONE, worn.go());
	}

	@Test
	void storedToolBeatsCrafting() {
		Builder b = base();
		b.stored = true;
		assertEquals(SelfCraftDecision.FETCH_STORED, b.go());
	}

	@Test
	void noPlan() {
		assertEquals(new SelfCraftDecision.Blocked(BlockReason.NO_MATERIALS), base().noPlan().go());
		assertEquals(SelfCraftDecision.NONE, base().own(WorkerToolState.WORN).noPlan().go());
		// a farmer never gets a blocked status
		assertEquals(SelfCraftDecision.NONE, base().mode(Mode.BONUS).noPlan().go());
	}

	@Test
	void lateInTheDay() {
		assertEquals(SelfCraftDecision.NONE, base().day(Balance.SELF_CRAFT_LAST_FETCH).go());
		assertInstanceOf(SelfCraftDecision.Start.class, base().day(Balance.SELF_CRAFT_LAST_FETCH - 1).go());
	}
}
