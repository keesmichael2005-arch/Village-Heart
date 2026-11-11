package dev.villageheart.core.heart;

import static dev.villageheart.core.heart.Rejection.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.guard.GuardShift;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.heart.net.HeartCodecs;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-12: the SetGuardShift action's gate, the shift view and its place in the villager row. */
class GuardShiftActionTest {
	private static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 64, 0);
	private static final UUID OWNER = UUID.randomUUID(), STEWARD = UUID.randomUUID(), RESIDENT = UUID.randomUUID();
	private static final Optional<VillageState> VILLAGE = Optional.of(VillageState.founded(HEART, OWNER, "O", 0)
			.withRank(STEWARD, Rank.STEWARD).withRank(RESIDENT, Rank.RESIDENT));

	private static Optional<Rejection> gate(UUID p, HeartAction a) { return ActionGate.check(true, true, true, VILLAGE, p, a); }

	@Test
	void choiceThreeIsBadShiftBeforeThePermissionCheck() {
		UUID g = UUID.randomUUID();
		assertEquals(Optional.of(BAD_SHIFT), gate(STEWARD, new HeartAction.SetGuardShift(g, 3)));
		assertEquals(Optional.of(BAD_SHIFT), gate(RESIDENT, new HeartAction.SetGuardShift(g, -1)), "malformed is checked before the permission");
		assertEquals(Optional.of(NO_PERMISSION), gate(RESIDENT, new HeartAction.SetGuardShift(g, 1)));
		for (int choice = 0; choice <= 2; choice++) assertEquals(Optional.empty(), gate(STEWARD, new HeartAction.SetGuardShift(g, choice)));
		assertEquals(Optional.empty(), gate(OWNER, new HeartAction.SetGuardShift(g, 2)));
	}

	@Test
	void shiftViewAndButtonCycle() {
		assertEquals(GuardShiftView.NONE, GuardShiftView.of(Optional.empty()));
		assertEquals(GuardShiftView.DAY_AUTO, GuardShiftView.of(Optional.of(new GuardShift(Shift.DAY, false))));
		assertEquals(GuardShiftView.NIGHT_PINNED, GuardShiftView.of(Optional.of(new GuardShift(Shift.NIGHT, true))));
		assertEquals(1, GuardShiftView.NIGHT_AUTO.nextChoice(), "auto -> day");
		assertEquals(2, GuardShiftView.DAY_PINNED.nextChoice(), "day -> night");
		assertEquals(0, GuardShiftView.NIGHT_PINNED.nextChoice(), "night -> auto");
	}

	@Test
	void everyShiftViewRoundTripsInARow() {
		for (GuardShiftView s : GuardShiftView.values()) {
			VillagerRow row = new VillagerRow(UUID.randomUUID(), "n", "guard", "x", 1, 0, 100, NeedsFlags.UNKNOWN, ActivityKind.PATROLLING, false, false,
					false, true, false, s);
			var buf = Unpooled.buffer();
			HeartCodecs.VILLAGER_ROW.encode(buf, row);
			assertEquals(row, HeartCodecs.VILLAGER_ROW.decode(buf));
			assertEquals(0, buf.readableBytes());
		}
	}
}
