package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The Heart shows a repairing builder as Repairing, and a short repair as Needs materials. */
class RepairActivityTest {
	@Test void blankIsNotARepair() {
		assertEquals(Optional.empty(), RepairActivity.of(null));
		assertEquals(Optional.empty(), RepairActivity.of(""));
		assertEquals(Optional.empty(), RepairActivity.of("  "));
	}

	@Test void repairingAndAShortItem() {
		assertEquals(ActivityKind.REPAIRING, RepairActivity.of("Repairing").orElseThrow());
		assertEquals("Repairing", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.REPAIRING, "builder")));
		assertEquals(ActivityKind.NEEDS_MATERIALS, RepairActivity.of("Repairs need 1 more fence gate").orElseThrow());
		assertEquals(ActivityKind.REPAIRING, RepairActivity.of("Repairs").orElseThrow());
	}
}
