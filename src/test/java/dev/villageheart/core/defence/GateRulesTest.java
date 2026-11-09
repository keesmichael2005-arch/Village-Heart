package dev.villageheart.core.defence;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.defence.GateRules.OpenGate;
import org.junit.jupiter.api.Test;

class GateRulesTest {
	@Test
	void mayOpenOnlyInTheClaimUnlockedAndWithoutHostiles() {
		assertTrue(GateRules.mayOpen(true, false, false, false));
		assertFalse(GateRules.mayOpen(false, false, false, false), "outside the claim");
		assertFalse(GateRules.mayOpen(true, true, false, false), "locked by redstone");
		assertFalse(GateRules.mayOpen(true, false, true, false), "a hostile is near");
		assertTrue(GateRules.mayOpen(true, false, true, true), "an engaged guard may open it anyway");
		assertFalse(GateRules.mayOpen(false, false, true, true), "but never outside the claim");
	}

	@Test
	void closesWhenNobodyNeedsIt() {
		assertTrue(GateRules.shouldClose(new OpenGate(50, false, false, false, false)));
		assertFalse(GateRules.shouldClose(new OpenGate(50, false, true, false, false)), "a worker is passing");
		assertFalse(GateRules.shouldClose(new OpenGate(Balance.GATE_MIN_OPEN_TICKS - 1, false, false, false, false)), "not before the minimum");
	}

	@Test
	void neverOnSomeoneStandingInItUntilTheSafetyLimit() {
		assertFalse(GateRules.shouldClose(new OpenGate(100, true, false, true, false)));
		assertTrue(GateRules.shouldClose(new OpenGate(Balance.GATE_MAX_OPEN_TICKS, true, true, false, false)), "the safety net");
	}

	@Test
	void aHostileClosesItUnlessAnEngagedGuardNeedsIt() {
		assertTrue(GateRules.shouldClose(new OpenGate(50, false, true, true, false)));
		assertFalse(GateRules.shouldClose(new OpenGate(50, false, true, true, true)));
	}
}
