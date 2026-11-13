package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MeetingRulesTest {
	@Test
	void aSleeperStaysInBedForTheMeetingAndTheWalkHome() {
		assertTrue(MeetingRules.stayAsleep(DayPhase.MEET, true));
		assertTrue(MeetingRules.stayAsleep(DayPhase.GO_HOME, true));
		assertFalse(MeetingRules.stayAsleep(DayPhase.MEET, false));
		assertFalse(MeetingRules.stayAsleep(DayPhase.WAKE, true));
		assertFalse(MeetingRules.stayAsleep(DayPhase.WORK, true));
		assertFalse(MeetingRules.stayAsleep(DayPhase.SLEEP, true));
	}
}
