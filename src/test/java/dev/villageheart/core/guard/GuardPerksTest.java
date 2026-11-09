package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class GuardPerksTest {
	@Test
	void healthAndDamageByLevel() {
		assertEquals(30.0, GuardPerks.maxHealth(1));
		assertEquals(40.0, GuardPerks.maxHealth(2));
		assertEquals(50.0, GuardPerks.maxHealth(3));
		assertEquals(0.0, GuardPerks.bonusDamage(1));
		assertEquals(1.0, GuardPerks.bonusDamage(2));
		assertEquals(2.0, GuardPerks.bonusDamage(3));
		assertEquals(50.0, GuardPerks.maxHealth(4));
		assertEquals(2.0, GuardPerks.bonusDamage(9));
		assertThrows(IllegalArgumentException.class, () -> GuardPerks.maxHealth(0));
		assertThrows(IllegalArgumentException.class, () -> GuardPerks.bonusDamage(0));
	}

	@Test
	void readFromBalance() {
		for (int l = 1; l <= 3; l++) assertEquals(Balance.GUARD_LEVEL_HEALTH[l - 1], GuardPerks.maxHealth(l));
	}
}
