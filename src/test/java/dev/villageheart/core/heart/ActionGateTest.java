package dev.villageheart.core.heart;

import static dev.villageheart.core.heart.Rejection.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionGateTest {
	private static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 64, 0);
	private static final UUID OWNER = UUID.randomUUID();
	private static final UUID STEWARD = UUID.randomUUID();
	private static final UUID RESIDENT = UUID.randomUUID();
	private static final UUID VISITOR = UUID.randomUUID();
	private static final Optional<VillageState> VILLAGE = Optional.of(VillageState.founded(HEART, OWNER, "O", 0)
			.withRank(STEWARD, Rank.STEWARD).withRank(RESIDENT, Rank.RESIDENT));

	private static final HeartAction ASSIGN = new HeartAction.Assign(UUID.randomUUID(), "farmer", Optional.empty());
	private static final HeartAction FIRE = new HeartAction.Fire(UUID.randomUUID());
	private static final HeartAction RECHECK = new HeartAction.Recheck(HEART);

	private static Optional<Rejection> check(boolean rate, boolean open, boolean epoch, Optional<VillageState> v, UUID p,
			HeartAction a) {
		return ActionGate.check(rate, open, epoch, v, p, a);
	}

	private static Optional<Rejection> ok(UUID p, HeartAction a) { return check(true, true, true, VILLAGE, p, a); }

	@Test void eachRejectionAlone() {
		assertEquals(Optional.of(RATE_LIMITED), check(false, true, true, VILLAGE, STEWARD, ASSIGN));
		assertEquals(Optional.of(NOT_OPEN), check(true, false, true, VILLAGE, STEWARD, ASSIGN));
		assertEquals(Optional.of(STALE), check(true, true, false, VILLAGE, STEWARD, ASSIGN));
		assertEquals(Optional.of(NO_VILLAGE), check(true, true, true, Optional.empty(), STEWARD, ASSIGN));
		assertEquals(Optional.of(NO_PERMISSION), ok(VISITOR, ASSIGN));
		assertEquals(Optional.empty(), ok(STEWARD, ASSIGN));
	}

	@Test void order() {
		// A flood of bad packets must be stopped by the rate limit before anything else is looked at.
		assertEquals(Optional.of(RATE_LIMITED), check(false, false, false, Optional.empty(), VISITOR, ASSIGN));
		assertEquals(Optional.of(NOT_OPEN), check(true, false, false, Optional.empty(), VISITOR, ASSIGN));
		assertEquals(Optional.of(STALE), check(true, true, false, Optional.empty(), VISITOR, ASSIGN));
		assertEquals(Optional.of(NO_VILLAGE), check(true, true, true, Optional.empty(), VISITOR, ASSIGN));
	}

	@Test void ranksPerAction() {
		assertEquals(Optional.of(NO_PERMISSION), ok(VISITOR, ASSIGN));
		assertEquals(Optional.of(NO_PERMISSION), ok(RESIDENT, ASSIGN));
		assertEquals(Optional.of(NO_PERMISSION), ok(RESIDENT, FIRE));
		assertEquals(Optional.of(NO_PERMISSION), ok(RESIDENT, RECHECK));
		assertEquals(Optional.empty(), ok(STEWARD, ASSIGN));
		assertEquals(Optional.empty(), ok(STEWARD, FIRE));
		assertEquals(Optional.empty(), ok(STEWARD, RECHECK));
		assertEquals(Optional.empty(), ok(OWNER, ASSIGN));
		assertEquals(Optional.empty(), ok(OWNER, FIRE));
		assertEquals(Optional.empty(), ok(OWNER, RECHECK));
	}

	@Test void crafted_rank_values_never_reach_the_state_code() {
		for (int bad : new int[] {0, -1, 4, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
			assertEquals(Optional.of(BAD_RANK), ok(OWNER, new HeartAction.SetRank(VISITOR, bad)), "SetRank " + bad);
		}
		for (int good = 1; good <= 3; good++) assertEquals(Optional.empty(), ok(OWNER, new HeartAction.SetRank(VISITOR, good)));
		assertEquals(Optional.of(BAD_RANK), ok(OWNER, new HeartAction.Transfer(STEWARD, -1)));
		assertEquals(Optional.of(BAD_RANK), ok(OWNER, new HeartAction.Transfer(STEWARD, 4)));
		assertEquals(Optional.empty(), ok(OWNER, new HeartAction.Transfer(STEWARD, 1)));
	}

	@Test void onlyTheOwnerManagesRanksAndTransfers() {
		assertEquals(Optional.of(NO_PERMISSION), ok(STEWARD, new HeartAction.SetRank(VISITOR, 2)));
		assertEquals(Optional.of(NO_PERMISSION), ok(RESIDENT, new HeartAction.SetRank(VISITOR, 2)));
		assertEquals(Optional.of(NO_PERMISSION), ok(VISITOR, new HeartAction.SetRank(VISITOR, 2)));
		assertEquals(Optional.of(NO_PERMISSION), ok(STEWARD, new HeartAction.Transfer(STEWARD, 1)));
		assertEquals(Optional.of(NO_PERMISSION), ok(STEWARD, new HeartAction.Transfer(RESIDENT, 2)));
		assertEquals(Optional.empty(), ok(OWNER, new HeartAction.SetRank(VISITOR, 2)));
	}

	@Test void malformedComesAfterTheEarlierChecks() {
		HeartAction bad = new HeartAction.SetRank(VISITOR, 99);
		assertEquals(Optional.of(RATE_LIMITED), check(false, true, true, VILLAGE, OWNER, bad));
		assertEquals(Optional.of(NOT_OPEN), check(true, false, true, VILLAGE, OWNER, bad));
		assertEquals(Optional.of(STALE), check(true, true, false, VILLAGE, OWNER, bad));
		assertEquals(Optional.of(NO_VILLAGE), check(true, true, true, Optional.empty(), OWNER, bad));
		assertEquals(Optional.of(BAD_RANK), check(true, true, true, VILLAGE, OWNER, bad));
	}

	@Test void rankIsReadFromTheCurrentState() {
		Optional<VillageState> demoted = Optional.of(VILLAGE.get().withRank(STEWARD, Rank.VISITOR));
		assertEquals(Optional.of(NO_PERMISSION), check(true, true, true, demoted, STEWARD, ASSIGN));
	}
}
