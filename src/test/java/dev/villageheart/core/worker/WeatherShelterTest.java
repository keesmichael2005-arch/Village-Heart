package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import org.junit.jupiter.api.Test;

/**
 * Rain, snow, and thunderstorms are one flag. During work time it sends a hired worker to the workplace.
 * An on-duty guard never waits. Clearing the flag during work time means work again.
 */
class WeatherShelterTest {
	@Test
	void precipitationDuringWorkTimeWaits() {
		for (DayPhase phase : DayPhase.values()) {
			boolean wait = phase == DayPhase.WORK;
			assertEquals(wait, WeatherShelter.workerWaits(true, phase), phase.name());
			assertFalse(WeatherShelter.workerWaits(false, phase), phase.name());
		}
	}

	@Test
	void clearingDuringWorkTimeMeansWorkAgain() {
		assertTrue(WeatherShelter.workerWaits(true, DayPhase.WORK));
		assertFalse(WeatherShelter.workerWaits(false, DayPhase.WORK));
	}

	@Test
	void clockOutsideWorkTimeIsUnchanged() {
		assertFalse(WeatherShelter.workerWaits(true, DayPhase.WAKE));
		assertFalse(WeatherShelter.workerWaits(true, DayPhase.GO_HOME));
		assertFalse(WeatherShelter.workerWaits(true, DayPhase.SLEEP));
	}

	@Test
	void onDutyGuardNeverWaitsAndATraineeDoes() {
		assertFalse(WeatherShelter.guardWaits(true, true, false));
		assertFalse(WeatherShelter.guardWaits(true, true, true));
		assertFalse(WeatherShelter.guardWaits(false, false, false));
		assertTrue(WeatherShelter.guardWaits(true, false, false));
		assertFalse(WeatherShelter.guardWaits(true, false, true));
	}

	@Test
	void tabLineIsAppended() {
		ActivityKind[] v = ActivityKind.values();
		assertEquals(ActivityKind.SHELTERING, v[36]);
		assertEquals(ActivityKind.NO_SHELTER, v[37]);
		assertEquals(ActivityKind.REPAIRING, v[38]);
		assertEquals(ActivityKind.OUT_OF_THE_RAIN, v[39]);
		assertEquals("Out of the rain", HeartTexts.EN.get(HeartTexts.activityKey(ActivityKind.OUT_OF_THE_RAIN, "farmer")));
	}
}
