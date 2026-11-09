package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

/** Ration and heal maths, training and the night-hire rule (M18 r3 owner design). */
class GuardRationTest {
	@Test
	void aBiteUsesOnePointAndHealsOnePoint() {
		var b = GuardRation.bite(5, 10.0, 20.0);
		assertEquals(4, b.ration());
		assertEquals(Balance.GUARD_HEAL_PER_POINT, b.heal());
	}

	@Test
	void noRationNoHealing() {
		var b = GuardRation.bite(0, 3.0, 20.0);
		assertEquals(0, b.ration());
		assertEquals(0.0, b.heal(), "no food in storage means no healing");
	}

	@Test
	void aGuardAtFullHealthDoesNotEat() {
		var b = GuardRation.bite(9, 20.0, 20.0);
		assertEquals(9, b.ration());
		assertEquals(0.0, b.heal());
	}

	@Test
	void aBiteNeverHealsPastTheMax() {
		var b = GuardRation.bite(9, 19.5, 20.0);
		assertEquals(0.5, b.heal(), 1e-9);
		assertEquals(8, b.ration());
	}

	@Test
	void eatingAFullRationHealsExactlyItsPoints() {
		int ration = Balance.GUARD_RATION_CAP;
		double hp = 1.0;
		while (ration > 0) {
			var b = GuardRation.bite(ration, hp, 40.0);
			hp += b.heal();
			ration = b.ration();
		}
		assertEquals(1.0 + Balance.GUARD_RATION_CAP * Balance.GUARD_HEAL_PER_POINT, hp, 1e-9);
	}

	@Test
	void refillTopsUpToTheCapAndWantsItBelowTheThreshold() {
		assertEquals(Balance.GUARD_RATION_CAP, GuardRation.add(10, 5));
		assertEquals(5, GuardRation.add(0, 5));
		assertTrue(GuardRation.wantsRefill(0));
		assertTrue(GuardRation.wantsRefill(Balance.GUARD_RATION_REFILL_BELOW - 1));
		assertFalse(GuardRation.wantsRefill(Balance.GUARD_RATION_REFILL_BELOW));
		assertThrows(IllegalArgumentException.class, () -> GuardRation.add(1, -1));
	}

	@Test
	void trainingEndsAtDuskOfTheHireDay() {
		assertEquals(12000, GuardTraining.qualifiesAt(0));
		assertEquals(12000, GuardTraining.qualifiesAt(5000));
		assertEquals(24000 + 12000, GuardTraining.qualifiesAt(24000 + 100));
		assertEquals(24000 + 12000, GuardTraining.qualifiesAt(13000), "a hire after dusk trains the next day");
	}

	@Test
	void aGuardIsQualifiedByTheEndOfItsTrainingDayAndCanFightThatNight() {
		long until = GuardTraining.qualifiesAt(2000);
		assertTrue(GuardTraining.training(11999, until));
		assertFalse(GuardTraining.training(12000, until));
		assertFalse(GuardTraining.training(14000, until), "night: qualified");
		assertFalse(GuardTraining.training(0, 0), "0 means qualified");
		assertTrue(new GuardData(false, until, 0).training(3000));
		assertFalse(GuardData.FRESH.training(3000));
	}

	@Test
	void guardsAreHiredByDayOnly() {
		assertTrue(GuardTraining.mayHire(0));
		assertTrue(GuardTraining.mayHire(11999));
		assertFalse(GuardTraining.mayHire(12000));
		assertFalse(GuardTraining.mayHire(23999));
		assertTrue(GuardTraining.mayHire(24000), "the next morning");
		assertTrue(GuardTraining.mayHire(5 * 24000 + 100));
		assertFalse(GuardTraining.mayHire(5 * 24000 + 14000));
	}

	@Test
	void dataValidatesItsFields() {
		assertThrows(IllegalArgumentException.class, () -> new GuardData(false, -1, 0));
		assertThrows(IllegalArgumentException.class, () -> new GuardData(false, 0, -1));
		assertEquals(new GuardData(true, 5, 3), GuardData.FRESH.withWounded(true).withTrainingUntil(5).withRation(3));
	}
}
