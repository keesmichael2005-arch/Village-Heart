package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Roadmap M12 L1: the wage bill by level. */
class WageRulesTest {
	@Test void wageByLevelFollowsTheBalanceTable() {
		assertEquals(1, WageRules.wage(1));
		assertEquals(1, WageRules.wage(2));
		assertEquals(2, WageRules.wage(3));
		for (int l = 1; l <= 3; l++) assertEquals(Balance.wageForLevel(l), WageRules.wage(l));
	}

	@Test void levelsOutsideTheTableClamp() {
		assertEquals(WageRules.wage(1), WageRules.wage(0));
		assertEquals(WageRules.wage(1), WageRules.wage(-5));
		assertEquals(WageRules.wage(3), WageRules.wage(4));
		assertEquals(WageRules.wage(3), WageRules.wage(9));
	}

	@Test void billSumsTheWages() {
		assertEquals(0, WageRules.bill(List.of()));
		assertEquals(4, WageRules.bill(List.of(1, 1, 3)));
		assertEquals(3, WageRules.bill(List.of(1, 1, 2)), "level 2 still earns 1");
		assertEquals(6, WageRules.bill(List.of(3, 3, 3)));
		assertEquals(3, WageRules.bill(List.of(1, 1, 1)));
	}
}
