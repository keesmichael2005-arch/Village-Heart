package dev.villageheart.siege;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.villageheart.core.siege.SiegeHits;
import org.junit.jupiter.api.Test;

class SiegeStatsTest {
	@Test
	void noonSticksAndABreachCountsByCause() {
		SiegeStats.resetForTests();
		SiegeStats.noteNoon(4);
		SiegeStats.noteNoon(9);
		SiegeStats.broken(SiegeHits.Attacker.ZOMBIE);
		SiegeStats.broken(SiegeHits.Attacker.RAVAGER);
		SiegeStats.brokenByCreeper();
		SiegeStats.repaired(1);
		SiegeStats.siegeStarted();
		SiegeStats.zombieSpawned();
		SiegeStats.zombieSpawned();
		SiegeStats.zombiesKilled(1);
		SiegeStats.villagerDeath();
		SiegeStats.path();
		SiegeStats.lure(3);
		SiegeStats.lure(1);
		SiegeStats.Counts c = SiegeStats.take();
		assertEquals(4, c.breachedNoon());
		assertEquals(3, c.blocksBroken());
		assertEquals(1, c.brokenZombie());
		assertEquals(1, c.brokenRavager());
		assertEquals(1, c.brokenCreeper());
		assertEquals(1, c.repairs());
		assertEquals(1, c.repairItems());
		assertEquals(1, c.started());
		assertEquals(2, c.zombies());
		assertEquals(1, c.killed());
		assertEquals(1, c.villagerDeaths());
		assertEquals(1, c.paths());
		assertEquals(3, c.maxLured());
		assertEquals(0, SiegeStats.take().blocksBroken());
		assertEquals(0, SiegeStats.take().breachedNoon());
	}
}
