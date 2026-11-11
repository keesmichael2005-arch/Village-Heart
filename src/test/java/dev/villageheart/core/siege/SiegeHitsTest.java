package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.siege.SiegeHits.Attacker;
import org.junit.jupiter.api.Test;

/** T-L1-16. Hit table, including a baby on the zombie row, and the creeper hole. */
class SiegeHitsTest {
	@Test
	void hitsToBreakMatchTheTable() {
		assertEquals(12, SiegeHits.hitsToBreak(Attacker.ZOMBIE, DefenceKind.STONE));
		assertEquals(4, SiegeHits.hitsToBreak(Attacker.ZOMBIE, DefenceKind.GATE));
		assertEquals(6, SiegeHits.hitsToBreak(Attacker.VINDICATOR, DefenceKind.STONE));
		assertEquals(2, SiegeHits.hitsToBreak(Attacker.VINDICATOR, DefenceKind.GATE));
		assertEquals(2, SiegeHits.hitsToBreak(Attacker.RAVAGER, DefenceKind.STONE));
		assertEquals(2, SiegeHits.hitsToBreak(Attacker.RAVAGER, DefenceKind.GATE));
		assertEquals(20, SiegeHits.interval(Attacker.ZOMBIE));
		assertEquals(20, SiegeHits.interval(Attacker.VINDICATOR));
		assertEquals(40, SiegeHits.interval(Attacker.RAVAGER));
		for (DefenceKind k : DefenceKind.values()) {
			assertEquals(SiegeHits.damage(Attacker.ZOMBIE, k), SiegeHits.damage(Attacker.BABY_ZOMBIE, k));
			assertEquals(SiegeHits.damage(Attacker.ZOMBIE, k), SiegeHits.damage(Attacker.HUSK, k));
			assertEquals(SiegeHits.damage(Attacker.ZOMBIE, k), SiegeHits.damage(Attacker.ZOMBIE_VILLAGER, k));
		}
	}

	@Test
	void creeperSplashRoundsAndPowerWidensTheCracks() {
		assertEquals(16, CreeperBlast.damage(0, true, false));
		assertEquals(16, CreeperBlast.damage(1, true, true));
		assertEquals(5, CreeperBlast.damage(1, false, false));
		assertEquals(4, CreeperBlast.damage(Math.sqrt(2), false, false));
		assertEquals(3, CreeperBlast.damage(2, false, false));
		assertEquals(0, CreeperBlast.damage(3, false, false));
		assertEquals(4, CreeperBlast.damage(3, false, true));
		assertEquals(0, CreeperBlast.damage(4, false, false));
		assertTrue(CreeperBlast.damage(4, false, true) > 0);
	}
}
