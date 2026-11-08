package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.mine.AwayCheck.Lease;
import dev.villageheart.core.mine.MinerPlanner.Action;
import dev.villageheart.core.mine.MinerPlanner.Decision;
import dev.villageheart.core.mine.MinerPlanner.PlanInputs;
import dev.villageheart.core.mine.MinerPlanner.StatusEffect;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MinerPlannerTest {
	/** A miner that is ready to go down. */
	private static PlanInputs ready() {
		return new PlanInputs(true, false, false, Lease.HELD, true, true, 3000, OptionalLong.empty(), 10);
	}

	private static PlanInputs edit(boolean slot, boolean carries, boolean pending, Lease lease, boolean shaft, boolean depth, long time,
			OptionalLong unreachable, double dist) {
		return new PlanInputs(slot, carries, pending, lease, shaft, depth, time, unreachable, dist);
	}

	private static void assertDecision(Action a, StatusEffect s, Decision d) {
		assertEquals(new Decision(a, s), d);
	}

	@Test
	void readyMinerGoesDown() {
		assertDecision(Action.GO_DOWN, StatusEffect.CLEAR, MinerPlanner.decide(ready()));
	}

	@Test
	void row1NoSlotIdles() {
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.CLEAR,
				MinerPlanner.decide(edit(false, true, true, Lease.NONE, false, false, 3000, OptionalLong.empty(), 10)));
	}

	@Test
	void row2CarryingOrPendingYieldsToTheDepositTrip() {
		assertDecision(Action.YIELD, StatusEffect.KEEP, MinerPlanner.decide(edit(true, true, false, Lease.HELD, true, true, 3000, OptionalLong.empty(), 10)));
		assertDecision(Action.YIELD, StatusEffect.KEEP, MinerPlanner.decide(edit(true, false, true, Lease.HELD, true, true, 3000, OptionalLong.empty(), 10)));
		// ore in the pockets and no pickaxe: deliver first
		assertDecision(Action.YIELD, StatusEffect.KEEP, MinerPlanner.decide(edit(true, true, false, Lease.NONE, true, true, 3000, OptionalLong.empty(), 10)));
	}

	@Test
	void row3UnknownLeaseKeepsTheStatus() {
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.KEEP, MinerPlanner.decide(edit(true, false, false, Lease.UNKNOWN, true, true, 3000, OptionalLong.empty(), 10)));
	}

	@Test
	void row4NoPickaxeNeedsATool() {
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.NEEDS_TOOL, MinerPlanner.decide(edit(true, false, false, Lease.NONE, true, true, 3000, OptionalLong.empty(), 10)));
		// no shaft and no pickaxe: the pickaxe is reported
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.NEEDS_TOOL, MinerPlanner.decide(edit(true, false, false, Lease.NONE, false, true, 3000, OptionalLong.empty(), 10)));
	}

	@Test
	void row5NoValidShaft() {
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.NO_WORK, MinerPlanner.decide(edit(true, false, false, Lease.HELD, false, true, 3000, OptionalLong.empty(), 10)));
	}

	@Test
	void row6LastDescent() {
		assertDecision(Action.GO_DOWN, StatusEffect.CLEAR, MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, true, 10399, OptionalLong.empty(), 10)));
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.CLEAR, MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, true, 10400, OptionalLong.empty(), 10)));
	}

	@Test
	void row6UnreachableRetryAfterSixHundredTicks() {
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.SITE_UNREACHABLE,
				MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, true, 3000, OptionalLong.of(599), 10)));
		assertDecision(Action.GO_DOWN, StatusEffect.CLEAR, MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, true, 3000, OptionalLong.of(600), 10)));
	}

	@Test
	void row6UnknownDepthIdlesWithStatusOk() {
		assertDecision(Action.IDLE_AT_MINE, StatusEffect.CLEAR, MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, false, 3000, OptionalLong.empty(), 10)));
	}

	@Test
	void row7FarMinerWalksToTheMineFirst() {
		assertDecision(Action.GO_DOWN, StatusEffect.CLEAR, MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, true, 3000, OptionalLong.empty(), 40)));
		assertDecision(Action.WALK_TO_MINE, StatusEffect.CLEAR, MinerPlanner.decide(edit(true, false, false, Lease.HELD, true, true, 3000, OptionalLong.empty(), 41)));
	}
}
