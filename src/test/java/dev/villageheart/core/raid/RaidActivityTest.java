package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.worker.DayPhase;
import org.junit.jupiter.api.Test;

/** T-L1-6. Under attack, a present worker shelters unless it is already asleep at home. */
class RaidActivityTest {
	@Test void whoShelters() {
		for (DayPhase phase : new DayPhase[] {DayPhase.WORK, DayPhase.WAKE, DayPhase.GO_HOME}) {
			assertTrue(RaidActivity.shelter(true, phase, false, true), phase.name());
			assertTrue(RaidActivity.shelter(true, phase, false, false), phase.name());
		}
		assertFalse(RaidActivity.shelter(true, DayPhase.SLEEP, false, true));
		assertTrue(RaidActivity.shelter(true, DayPhase.SLEEP, false, false));
		assertFalse(RaidActivity.shelter(true, DayPhase.WORK, true, false));
		assertFalse(RaidActivity.shelter(false, DayPhase.WORK, false, false));
		assertFalse(RaidActivity.shelter(false, DayPhase.SLEEP, false, false));
	}

	@Test void shelterKindsStayAtTheEnd() {
		ActivityKind[] v = ActivityKind.values();
		assertEquals(ActivityKind.SHELTERING, v[32]);
		assertEquals(ActivityKind.NO_SHELTER, v[33]);
	}
}
